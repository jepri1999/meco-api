package uk.thepragmaticdev.webhook;

import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.thepragmaticdev.billing.BillingService;
import uk.thepragmaticdev.exception.ApiException;
import uk.thepragmaticdev.exception.code.WebhookCode;

@Log4j2
@Service
public class WebhookService {

  private final BillingService billingService;

  private final String stripeWebhookSignature;

  /**
   * Service for handling webhook requests.
   * 
   * @param billingService The service for updating billing information
   */
  @Autowired
  public WebhookService(BillingService billingService,
      @Value("${stripe.webhook-signature}") String stripeWebhookSignature) {
    this.billingService = billingService;
    this.stripeWebhookSignature = stripeWebhookSignature;
  }

  /**
   * Process events sent by stripe to meco. A subscription cancellation event will
   * be sent automatically by stripe if all retries for a failed payment decline.
   * 
   * @param payload   Payload sent by stripe
   * @param sigHeader Secret used to generate the signature
   */
  public void handleStripeEvent(String payload, String sigHeader) {
    var event = validateStripeSignatureAndConstructEvent(payload, sigHeader);
    switch (event.getType()) {
      case "invoice.payment_succeeded":
      case "invoice.payment_failed":
        handleInvoicePayment(event.getDataObjectDeserializer().getObject());
        break;
      case "customer.subscription.deleted":
        handleDelinquentCustomer(event.getDataObjectDeserializer().getObject());
        break;
      default:
        log.debug("No action for stripe event {}", event.getType());
    }
  }

  private void handleInvoicePayment(Optional<StripeObject> stripeObject) {
    if (!stripeObject.isPresent()) {
      throw new ApiException(WebhookCode.OBJECT_MISSING_ERROR);
    }
    var invoice = (Invoice) stripeObject.get();
    log.info("Handling invoice payment: {}", invoice.getNumber());
    billingService.syncSubscription(invoice.getCustomer(), invoice.getSubscription());
  }

  private void handleDelinquentCustomer(Optional<StripeObject> stripeObject) {
    if (!stripeObject.isPresent()) {
      throw new ApiException(WebhookCode.OBJECT_MISSING_ERROR);
    }
    var subscription = (Subscription) stripeObject.get();
    log.info("Handling delinquent customer: {}", subscription.getCustomer());
    billingService.handleDelinquentCustomer(subscription);
  }

  private Event validateStripeSignatureAndConstructEvent(String payload, String sigHeader) {
    try {
      return Webhook.constructEvent(payload, sigHeader, stripeWebhookSignature);
    } catch (JsonSyntaxException e) {
      throw new ApiException(WebhookCode.DESERIALIIZATION_ERROR);
    } catch (SignatureVerificationException e) {
      throw new ApiException(WebhookCode.SIGNATURE_VERIFICATION_ERROR);
    }
  }
}
