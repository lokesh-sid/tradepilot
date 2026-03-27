package tradingbot.infrastructure.marketdata.hyperliquid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tradingbot.infrastructure.marketdata.hyperliquid.HyperliquidOrderSigner.HlSignature;
import tradingbot.infrastructure.marketdata.hyperliquid.HyperliquidOrderSigner.HyperliquidSigningException;

/**
 * Unit tests for {@link HyperliquidOrderSigner}.
 *
 * <p>Uses the standard Ganache account #0 private key — a well-known test key that
 * must never be used with real funds.
 */
@DisplayName("HyperliquidOrderSigner")
class HyperliquidOrderSignerTest {

    /**
     * Ganache default mnemonic account #0.
     * Private key: 0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d
     * Address:     0x90F8bf6A479f320ead074411a4B0e7944Ea8c9C1
     */
    private static final String TEST_PRIVATE_KEY =
            "0x4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d";
    private static final String TEST_ADDRESS =
            "0x90f8bf6a479f320ead074411a4b0e7944ea8c9c1";

    private HyperliquidOrderSigner testnetSigner;
    private HyperliquidOrderSigner mainnetSigner;

    @BeforeEach
    void setUp() {
        testnetSigner = new HyperliquidOrderSigner(TEST_PRIVATE_KEY, true);
        mainnetSigner = new HyperliquidOrderSigner(TEST_PRIVATE_KEY, false);
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void derivesCorrectAddress() {
            assertThat(testnetSigner.getAddress()).isEqualToIgnoringCase(TEST_ADDRESS);
        }

        @Test
        void testnetAndMainnetDeriveSameAddress() {
            assertThat(testnetSigner.getAddress()).isEqualToIgnoringCase(mainnetSigner.getAddress());
        }

        @Test
        void rejectsBlankKey() {
            assertThatThrownBy(() -> new HyperliquidOrderSigner("", true))
                    .isInstanceOf(Exception.class);
        }

        @Test
        void rejectsInvalidHex() {
            assertThatThrownBy(() -> new HyperliquidOrderSigner("0xnotvalidhex!!", true))
                    .isInstanceOf(Exception.class);
        }

        @Test
        void acceptsKeyWithout0xPrefix() {
            String withoutPrefix = TEST_PRIVATE_KEY.substring(2);
            HyperliquidOrderSigner signer = new HyperliquidOrderSigner(withoutPrefix, true);
            assertThat(signer.getAddress()).isEqualToIgnoringCase(TEST_ADDRESS);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("sign — signature format")
    class SignatureFormat {

        @Test
        void rAndSAre66CharHexStrings() {
            HlSignature sig = testnetSigner.sign(btcOrderAction(), 1_000_000L);

            // 0x + 64 hex chars = 32 bytes
            assertThat(sig.r()).startsWith("0x").hasSize(66);
            assertThat(sig.s()).startsWith("0x").hasSize(66);
        }

        @Test
        void vIs27Or28() {
            HlSignature sig = testnetSigner.sign(btcOrderAction(), 1_000_000L);
            assertThat(sig.v()).isIn(27, 28);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("sign — determinism and sensitivity")
    class SignatureSensitivity {

        @Test
        void sameInputProducesSameSignature() {
            LinkedHashMap<String, Object> action = btcOrderAction();
            long nonce = 1_234_567_890L;

            HlSignature sig1 = testnetSigner.sign(action, nonce);
            HlSignature sig2 = testnetSigner.sign(action, nonce);

            assertThat(sig1.r()).isEqualTo(sig2.r());
            assertThat(sig1.s()).isEqualTo(sig2.s());
            assertThat(sig1.v()).isEqualTo(sig2.v());
        }

        @Test
        void differentNonce_producesDifferentSignature() {
            LinkedHashMap<String, Object> action = btcOrderAction();

            HlSignature sig1 = testnetSigner.sign(action, 1_000L);
            HlSignature sig2 = testnetSigner.sign(action, 2_000L);

            assertThat(sig1.r()).isNotEqualTo(sig2.r());
        }

        @Test
        void testnet_and_mainnet_produceDifferentSignatures() {
            LinkedHashMap<String, Object> action = btcOrderAction();
            long nonce = 9_999_999_999L;

            HlSignature testSig = testnetSigner.sign(action, nonce);
            HlSignature mainSig = mainnetSigner.sign(action, nonce);

            // source differs ("a" vs "b") → different EIP-712 message hash → different signature
            assertThat(testSig.r()).isNotEqualTo(mainSig.r());
        }

        @Test
        void differentAction_producesDifferentSignature() {
            long nonce = 5_000L;

            HlSignature buy  = testnetSigner.sign(orderAction("BTC", true,  "50000", "0.001"), nonce);
            HlSignature sell = testnetSigner.sign(orderAction("BTC", false, "50000", "0.001"), nonce);

            assertThat(buy.r()).isNotEqualTo(sell.r());
        }

        @Test
        void withVault_producesDifferentSignatureThanNoVault() {
            LinkedHashMap<String, Object> action = btcOrderAction();
            long nonce = 1_234L;

            HlSignature noVault   = testnetSigner.sign(action, nonce, null);
            HlSignature withVault = testnetSigner.sign(action, nonce,
                    "0xabcdef1234567890abcdef1234567890abcdef12");

            assertThat(noVault.r()).isNotEqualTo(withVault.r());
        }

        @Test
        void blankVaultEqualsNullVault() {
            LinkedHashMap<String, Object> action = btcOrderAction();
            long nonce = 7_777L;

            HlSignature nullVault  = testnetSigner.sign(action, nonce, null);
            HlSignature blankVault = testnetSigner.sign(action, nonce, "");

            assertThat(nullVault.r()).isEqualTo(blankVault.r());
            assertThat(nullVault.s()).isEqualTo(blankVault.s());
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("computeConnectionId")
    class ConnectionId {

        @Test
        void producesExactly32Bytes() throws Exception {
            byte[] msgpack = testnetSigner.toMsgpack(btcOrderAction());
            byte[] id = testnetSigner.computeConnectionId(msgpack, null, 12_345L);

            assertThat(id).hasSize(32);
        }

        @Test
        void nullVault_differsFromNonNullVault() throws Exception {
            byte[] msgpack = testnetSigner.toMsgpack(btcOrderAction());
            String vault = "0xabcdef1234567890abcdef1234567890abcdef12";

            byte[] noVault  = testnetSigner.computeConnectionId(msgpack, null, 999L);
            byte[] withVault = testnetSigner.computeConnectionId(msgpack, vault, 999L);

            assertThat(noVault).isNotEqualTo(withVault);
        }

        @Test
        void differentNonce_producesDifferentId() throws Exception {
            byte[] msgpack = testnetSigner.toMsgpack(btcOrderAction());

            byte[] id1 = testnetSigner.computeConnectionId(msgpack, null, 1L);
            byte[] id2 = testnetSigner.computeConnectionId(msgpack, null, 2L);

            assertThat(id1).isNotEqualTo(id2);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("toMsgpack")
    class Msgpack {

        @Test
        void serializesStringMap() throws Exception {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("type", "order");
            assertThat(testnetSigner.toMsgpack(m)).isNotEmpty();
        }

        @Test
        void serializesBooleans() throws Exception {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("isBuy", true);
            m.put("reduceOnly", false);
            assertThat(testnetSigner.toMsgpack(m)).isNotEmpty();
        }

        @Test
        void serializesNestedList() throws Exception {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("orders", List.of(Map.of("p", "50000")));
            assertThat(testnetSigner.toMsgpack(m)).isNotEmpty();
        }

        @Test
        void serializesNullValue() throws Exception {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("cloid", null);
            assertThat(testnetSigner.toMsgpack(m)).isNotEmpty();
        }

        @Test
        void differentInputProducesDifferentBytes() throws Exception {
            LinkedHashMap<String, Object> buy = new LinkedHashMap<>();
            buy.put("b", true);
            LinkedHashMap<String, Object> sell = new LinkedHashMap<>();
            sell.put("b", false);

            assertThat(testnetSigner.toMsgpack(buy))
                    .isNotEqualTo(testnetSigner.toMsgpack(sell));
        }

        @Test
        void rejectsUnknownType() {
            assertThatThrownBy(() -> testnetSigner.toMsgpack(new Object()))
                    .isInstanceOf(HyperliquidSigningException.class)
                    .hasMessageContaining("Unsupported type");
        }

        @Test
        void mapKeyOrderAffectsBytes() throws Exception {
            LinkedHashMap<String, Object> ab = new LinkedHashMap<>();
            ab.put("a", 1);
            ab.put("b", 2);

            LinkedHashMap<String, Object> ba = new LinkedHashMap<>();
            ba.put("b", 2);
            ba.put("a", 1);

            // msgpack encodes maps in insertion order, so key ordering matters
            assertThat(testnetSigner.toMsgpack(ab))
                    .isNotEqualTo(testnetSigner.toMsgpack(ba));
        }
    }

    // -------------------------------------------------------------------------
    // Test action builders
    // -------------------------------------------------------------------------

    /** Canonical BTC limit buy order action in Hyperliquid wire format. */
    private static LinkedHashMap<String, Object> btcOrderAction() {
        return orderAction("BTC", true, "50000.0", "0.001");
    }

    /**
     * Builds an order action map in the insertion order expected by the Hyperliquid Python SDK.
     * The inner order uses the abbreviated field names ({@code a}, {@code b}, {@code p}, etc.)
     * that the wire protocol requires.
     */
    private static LinkedHashMap<String, Object> orderAction(
            String coin, boolean isBuy, String limitPx, String sz) {

        LinkedHashMap<String, Object> order = new LinkedHashMap<>();
        order.put("a", 0);          // asset index
        order.put("b", isBuy);
        order.put("p", limitPx);
        order.put("s", sz);
        order.put("r", false);      // reduceOnly
        order.put("t", limitGtc()); // orderType

        LinkedHashMap<String, Object> action = new LinkedHashMap<>();
        action.put("type", "order");
        action.put("orders", List.of(order));
        action.put("grouping", "na");
        return action;
    }

    private static Map<String, Object> limitGtc() {
        return Map.of("limit", Map.of("tif", "Gtc"));
    }
}
