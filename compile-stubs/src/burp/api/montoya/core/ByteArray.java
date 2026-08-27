package burp.api.montoya.core;
public interface ByteArray {
 static ByteArray byteArray(byte... data) {
  final byte[] copy = data == null ? new byte[0] : java.util.Arrays.copyOf(data, data.length);
  return new ByteArray() {
   public int length() { return copy.length; }
   public String toString() { return new String(copy, java.nio.charset.StandardCharsets.UTF_8); }
   public byte[] getBytes() { return java.util.Arrays.copyOf(copy, copy.length); }
  };
 }
 int length();
 String toString();
 byte[] getBytes();
}
