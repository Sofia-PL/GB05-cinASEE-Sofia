package com.cinasee.componente2_reservas.services.payment;

import com.cinasee.componente2_reservas.TestData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la firma HMAC_SHA256_V1 de Redsys.
 *
 * El vector de referencia se calculó con una implementación INDEPENDIENTE (Python +
 * pycryptodome) del algoritmo del manual de Redsys, y esa misma implementación se probó
 * contra el servidor real de pruebas de Redsys: con la firma correcta devuelve la
 * "Pantalla de pago" y con una incorrecta el error SIS0042 (firma incorrecta).
 * Usa la clave inventada de los tests, no la de Redsys.
 */
class RedsysSignatureTest {

    private static final String ORDER = "000089011234";
    /** Base64 de {"DS_MERCHANT_AMOUNT":"1530","DS_MERCHANT_ORDER":"000089011234"} */
    private static final String PARAMS =
            "eyJEU19NRVJDSEFOVF9BTU9VTlQiOiIxNTMwIiwiRFNfTUVSQ0hBTlRfT1JERVIiOiIwMDAwODkwMTEyMzQifQ==";
    private static final String EXPECTED_SIGNATURE = "W+YueOr7l11QYxqIEPIHLEwhg8SxVsJUPQlPBG/TzdA=";

    private final RedsysSignature signature = new RedsysSignature(TestData.TEST_REDSYS_KEY);

    @Test
    void coincideConLaImplementacionDeReferencia() {
        assertThat(signature.sign(ORDER, PARAMS)).isEqualTo(EXPECTED_SIGNATURE);
    }

    @Test
    void verificaLaFirmaEnBase64EstandarYUrlSafe() {
        assertThat(signature.verify(ORDER, PARAMS, EXPECTED_SIGNATURE)).isTrue();
        // Redsys firma sus respuestas en Base64 URL-safe ('-' y '_')
        String urlSafe = EXPECTED_SIGNATURE.replace('+', '-').replace('/', '_');
        assertThat(signature.verify(ORDER, PARAMS, urlSafe)).isTrue();
    }

    @Test
    void rechazaParametrosManipulados() {
        String tampered = PARAMS.replace("IxNTMw", "IxNTMx"); // "1530" → "1531": otro importe
        assertThat(tampered).isNotEqualTo(PARAMS);
        assertThat(signature.verify(ORDER, tampered, EXPECTED_SIGNATURE)).isFalse();
    }

    @Test
    void rechazaOtroPedidoUOtraClave() {
        assertThat(signature.verify("000089019999", PARAMS, EXPECTED_SIGNATURE)).isFalse();
        RedsysSignature otherKey = new RedsysSignature("c3VwZXJzZWNyZXRhY2xhdmVkZXBydWVi");
        assertThat(otherKey.verify(ORDER, PARAMS, EXPECTED_SIGNATURE)).isFalse();
    }

    @Test
    void rechazaValoresNulos() {
        assertThat(signature.verify(null, PARAMS, EXPECTED_SIGNATURE)).isFalse();
        assertThat(signature.verify(ORDER, PARAMS, null)).isFalse();
    }

    @Test
    void laClaveDebeOcupar24Bytes() {
        assertThatThrownBy(() -> new RedsysSignature("Y29ydGE="))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodificaBase64UrlSafeSinRelleno() {
        String urlSafeNoPadding = PARAMS.replace("=", "");
        assertThat(new String(RedsysSignature.decodeAnyBase64(urlSafeNoPadding)))
                .contains("\"DS_MERCHANT_AMOUNT\":\"1530\"");
    }
}
