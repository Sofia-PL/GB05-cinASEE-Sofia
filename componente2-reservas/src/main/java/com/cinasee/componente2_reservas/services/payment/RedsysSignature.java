package com.cinasee.componente2_reservas.services.payment;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * Firma de mensajes del TPV Virtual de Redsys, versión {@code HMAC_SHA256_V1}.
 *
 * <p>Algoritmo (Manual de integración por Redirección de Redsys, apartado 3.4):</p>
 * <ol>
 *   <li><b>Clave derivada por operación:</b> se cifra el número de pedido
 *       ({@code Ds_Merchant_Order}) con 3DES en modo CBC, IV de ceros y relleno de
 *       ceros, usando como clave la clave secreta del terminal decodificada de Base64.</li>
 *   <li><b>Firma:</b> HMAC-SHA256 del texto {@code Ds_MerchantParameters} (el JSON
 *       ya codificado en Base64) con la clave derivada, codificado en Base64.</li>
 * </ol>
 *
 * <p>Las respuestas de Redsys (retorno del navegador y notificación) vienen firmadas
 * en Base64 "URL-safe" ({@code -} y {@code _} en lugar de {@code +} y {@code /}).
 * {@link #verify} acepta ambas variantes y compara en tiempo constante para no dar
 * pistas a un atacante.</p>
 *
 * <p>Solo usa la criptografía estándar de Java: no añade dependencias.</p>
 */
public final class RedsysSignature {

    /** Único valor de Ds_SignatureVersion que admite Redsys actualmente. */
    public static final String VERSION = "HMAC_SHA256_V1";

    private final byte[] secretKey;

    /**
     * @param secretKeyBase64 clave secreta del terminal tal como la da Redsys (Base64, 24 bytes)
     */
    public RedsysSignature(String secretKeyBase64) {
        byte[] key = Base64.getDecoder().decode(secretKeyBase64.trim());
        if (key.length != 24) {
            throw new IllegalArgumentException("La clave de Redsys debe ocupar 24 bytes (32 caracteres en Base64)");
        }
        this.secretKey = key;
    }

    /** Firma {@code merchantParametersBase64} para el pedido indicado (Base64 estándar). */
    public String sign(String order, String merchantParametersBase64) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(deriveKey(order), "HmacSHA256"));
            byte[] digest = mac.doFinal(merchantParametersBase64.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma de Redsys", e);
        }
    }

    /**
     * Comprueba la firma recibida de Redsys.
     *
     * @return true solo si coincide exactamente con la que calculamos con nuestra clave
     */
    public boolean verify(String order, String merchantParametersBase64, String receivedSignature) {
        if (order == null || merchantParametersBase64 == null || receivedSignature == null) {
            return false;
        }
        byte[] expected = toUrlSafe(sign(order, merchantParametersBase64)).getBytes(StandardCharsets.US_ASCII);
        byte[] received = toUrlSafe(receivedSignature.trim()).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, received);
    }

    /** 3DES-CBC (IV de ceros) del número de pedido rellenado con ceros hasta múltiplo de 8. */
    private byte[] deriveKey(String order) throws GeneralSecurityException {
        byte[] data = order.getBytes(StandardCharsets.UTF_8);
        int padded = ((data.length + 7) / 8) * 8;
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "DESede"), new IvParameterSpec(new byte[8]));
        return cipher.doFinal(Arrays.copyOf(data, padded));
    }

    /** Normaliza Base64 a su variante URL-safe y sin relleno, para comparar ambas. */
    private static String toUrlSafe(String base64) {
        return base64.replace('+', '-').replace('/', '_').replace("=", "");
    }

    /** Decodifica Base64 en cualquiera de sus dos variantes (estándar o URL-safe). */
    public static byte[] decodeAnyBase64(String value) {
        String normalized = value.trim().replace('-', '+').replace('_', '/');
        int missing = (4 - normalized.length() % 4) % 4;
        return Base64.getDecoder().decode(normalized + "=".repeat(missing));
    }
}
