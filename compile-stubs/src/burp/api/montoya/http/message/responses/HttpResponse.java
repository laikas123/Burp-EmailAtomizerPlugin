package burp.api.montoya.http.message.responses;
public interface HttpResponse {
 short statusCode(); String bodyToString(); String headerValue(String name); HttpResponse copyToTempFile();
 static HttpResponse httpResponse() { return null; }
 static HttpResponse httpResponse(String response) { return null; }
}
