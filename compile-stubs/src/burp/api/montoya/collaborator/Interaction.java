package burp.api.montoya.collaborator;
import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.Optional;
public interface Interaction {
    InteractionId id();
    InteractionType type();
    ZonedDateTime timeStamp();
    InetAddress clientIp();
    int clientPort();
    Optional<DnsDetails> dnsDetails();
    Optional<HttpDetails> httpDetails();
    Optional<SmtpDetails> smtpDetails();
    Optional<String> customData();
}
