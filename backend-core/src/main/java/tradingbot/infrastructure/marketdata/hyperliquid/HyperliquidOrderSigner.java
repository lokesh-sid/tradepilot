package tradingbot.infrastructure.marketdata.hyperliquid;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * EIP-712 signer for Hyperliquid L1 actions.
 *
 * <p>Implements the Hyperliquid "phantom agent" signing protocol:
 * <ol>
 *   <li>Serialize the action to msgpack bytes.</li>
 *   <li>Compute {@code connectionId = keccak256(msgpack || vaultPrefix || nonce_be8)}.</li>
 *   <li>Sign an EIP-712 {@code Agent} struct {@code {source, connectionId}} using the domain
 *       {@code {name:"Exchange", version:"1", chainId:1337, verifyingContract:0x0000...}}.</li>
 * </ol>
 *
 * <p>The {@code source} field is {@code "a"} for mainnet and {@code "b"} for testnet.
 * The domain {@code chainId} is always {@code 1337} — Hyperliquid's own L1 — regardless of
 * which REST/WebSocket endpoint (testnet vs mainnet) the caller uses.
 *
 * <p>Thread-safe after construction.
 */
public class HyperliquidOrderSigner {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidOrderSigner.class);

    /** Hyperliquid's own L1 chain ID — always 1337 for EIP-712 domain. */
    private static final long HL_CHAIN_ID = 1337L;

    private static final String DOMAIN_NAME    = "Exchange";
    private static final String DOMAIN_VERSION = "1";
    private static final String ZERO_ADDRESS   = "0x0000000000000000000000000000000000000000";

    private final ECKeyPair ecKeyPair;
    /** "a" = mainnet, "b" = testnet — used as the EIP-712 Agent.source field. */
    private final String source;

    /**
     * @param privateKeyHex hex-encoded secp256k1 private key (with or without "0x" prefix)
     * @param useTestnet    {@code true} for testnet ({@code source="b"}),
     *                      {@code false} for mainnet ({@code source="a"})
     */
    public HyperliquidOrderSigner(String privateKeyHex, boolean useTestnet) {
        BigInteger privateKey = Numeric.toBigInt(privateKeyHex);
        this.ecKeyPair = ECKeyPair.create(privateKey);
        this.source    = useTestnet ? "b" : "a";
        log.info("HyperliquidOrderSigner initialized [testnet={}, address={}]",
                useTestnet, Credentials.create(ecKeyPair).getAddress());
    }

    /**
     * Returns the Ethereum address derived from the configured private key.
     * Use this to confirm the correct wallet is loaded.
     */
    public String getAddress() {
        return Credentials.create(ecKeyPair).getAddress();
    }

    /**
     * Signs an action with no vault (standard agent signing).
     *
     * @param action insertion-ordered map representing the action (must match Python SDK key order)
     * @param nonce  epoch-milliseconds timestamp; must be unique per request
     * @return EIP-712 signature ready for inclusion in the {@code /exchange} request body
     */
    public HlSignature sign(Map<String, Object> action, long nonce) {
        return sign(action, nonce, null);
    }

    /**
     * Signs an action, optionally scoped to a vault address.
     *
     * @param action       insertion-ordered map representing the action
     * @param nonce        epoch-milliseconds timestamp; must be unique per request
     * @param vaultAddress optional vault address (null or blank → no vault prefix)
     * @return EIP-712 signature ready for inclusion in the {@code /exchange} request body
     */
    public HlSignature sign(Map<String, Object> action, long nonce, String vaultAddress) {
        try {
            byte[] msgpackBytes = toMsgpack(action);
            byte[] connectionId = computeConnectionId(msgpackBytes, vaultAddress, nonce);
            return signAgent(connectionId);
        } catch (HyperliquidSigningException e) {
            throw e;
        } catch (Exception e) {
            throw new HyperliquidSigningException("Failed to sign Hyperliquid action", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal signing steps
    // -------------------------------------------------------------------------

    /**
     * Computes the 32-byte {@code connectionId}:
     * <pre>
     *   keccak256( msgpack(action) || vaultPrefix || nonce_big_endian_8bytes )
     * </pre>
     *
     * {@code vaultPrefix}:
     * <ul>
     *   <li>no vault → single byte {@code 0x00}</li>
     *   <li>with vault → {@code 0x01} followed by the 20-byte address</li>
     * </ul>
     */
    byte[] computeConnectionId(byte[] msgpackBytes, String vaultAddress, long nonce) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(msgpackBytes.length + 29);
        try {
            buf.write(msgpackBytes);
            if (vaultAddress == null || vaultAddress.isBlank()) {
                buf.write(0x00);
            } else {
                buf.write(0x01);
                buf.write(Numeric.hexStringToByteArray(vaultAddress));
            }
            buf.write(longToBeBytes8(nonce));
        } catch (Exception e) {
            throw new HyperliquidSigningException("Failed to compute connectionId", e);
        }
        return Hash.sha3(buf.toByteArray());
    }

    /**
     * Signs the EIP-712 {@code Agent} struct.
     *
     * <pre>
     * domain      : { name:"Exchange", version:"1", chainId:1337, verifyingContract:0x0000... }
     * primaryType : "Agent"
     * types.Agent : [ {name:"source",type:"string"}, {name:"connectionId",type:"bytes32"} ]
     * message     : { source: "a"/"b", connectionId: 0x<32-byte-hex> }
     * </pre>
     */
    private HlSignature signAgent(byte[] connectionId) throws Exception {
        // Numeric.toHexString produces a 0x-prefixed string; pad to exactly 32 bytes (66 chars).
        String connectionIdHex = Numeric.toHexStringNoPrefixZeroPadded(
                Numeric.toBigInt(connectionId), 64);
        connectionIdHex = "0x" + connectionIdHex;

        String json = """
                {
                  "types": {
                    "EIP712Domain": [
                      {"name":"name","type":"string"},
                      {"name":"version","type":"string"},
                      {"name":"chainId","type":"uint256"},
                      {"name":"verifyingContract","type":"address"}
                    ],
                    "Agent": [
                      {"name":"source","type":"string"},
                      {"name":"connectionId","type":"bytes32"}
                    ]
                  },
                  "primaryType": "Agent",
                  "domain": {
                    "name": "%s",
                    "version": "%s",
                    "chainId": %d,
                    "verifyingContract": "%s"
                  },
                  "message": {
                    "source": "%s",
                    "connectionId": "%s"
                  }
                }
                """.formatted(DOMAIN_NAME, DOMAIN_VERSION, HL_CHAIN_ID, ZERO_ADDRESS,
                source, connectionIdHex);

        // Sign.signTypedData handles StructuredDataEncoder + keccak256 + secp256k1 signing.
        Sign.SignatureData sig = Sign.signTypedData(json, ecKeyPair);

        return new HlSignature(
                Numeric.toHexString(sig.getR()),
                Numeric.toHexString(sig.getS()),
                Numeric.toBigInt(sig.getV()).intValue());
    }

    // -------------------------------------------------------------------------
    // Msgpack serialization
    // -------------------------------------------------------------------------

    /**
     * Serializes a nested structure (Map, List, String, Number, Boolean, null) to msgpack bytes.
     *
     * <p>Map iteration order is preserved — callers must use insertion-ordered maps
     * (e.g. {@link java.util.LinkedHashMap}) to match the Python SDK's dict ordering,
     * which determines the {@code connectionId} hash.
     */
    @SuppressWarnings("unchecked")
    byte[] toMsgpack(Object value) throws Exception {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        packValue(packer, value);
        packer.close();
        return packer.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private void packValue(MessageBufferPacker packer, Object value) throws Exception {
        if (value == null) {
            packer.packNil();
        } else if (value instanceof Boolean b) {
            packer.packBoolean(b);
        } else if (value instanceof Integer i) {
            packer.packInt(i);
        } else if (value instanceof Long l) {
            packer.packLong(l);
        } else if (value instanceof Double d) {
            packer.packDouble(d);
        } else if (value instanceof Number n) {
            packer.packDouble(n.doubleValue());
        } else if (value instanceof String s) {
            packer.packString(s);
        } else if (value instanceof List<?> list) {
            packer.packArrayHeader(list.size());
            for (Object item : list) {
                packValue(packer, item);
            }
        } else if (value instanceof Map<?, ?> map) {
            packer.packMapHeader(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                packValue(packer, entry.getKey());
                packValue(packer, entry.getValue());
            }
        } else {
            throw new HyperliquidSigningException(
                    "Unsupported type for msgpack serialization: " + value.getClass().getName());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] longToBeBytes8(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    // -------------------------------------------------------------------------
    // Public types
    // -------------------------------------------------------------------------

    /**
     * EIP-712 signature components as expected by the Hyperliquid {@code /exchange} API:
     * <pre>{"r": "0x...", "s": "0x...", "v": 27}</pre>
     */
    public record HlSignature(String r, String s, int v) {}

    /** Thrown when action signing or msgpack serialization fails. */
    public static class HyperliquidSigningException extends RuntimeException {
        public HyperliquidSigningException(String message) {
            super(message);
        }
        public HyperliquidSigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
