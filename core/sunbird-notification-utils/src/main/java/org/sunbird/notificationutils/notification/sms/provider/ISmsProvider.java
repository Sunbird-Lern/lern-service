package org.sunbird.notificationutils.notification.sms.provider;

import java.util.List;
import java.util.Map;
import org.sunbird.keys.JsonKey;
import org.sunbird.notificationutils.notification.utils.SmsTemplateUtil;
import org.sunbird.request.RequestContext;

/** Interface for SMS provider integrations. Defines methods for sending SMS notifications. */
public interface ISmsProvider {

  String MSG_91_PROVIDER = JsonKey.MSG_91;
  String NIC_PROVIDER = JsonKey.NIC;

  /**
   * Retrieves the template ID for a given SMS content and provider based on pattern matching.
   *
   * @param sms The SMS text content.
   * @param provider The provider name.
   * @return The matched template ID, or empty string if no match found.
   */
  default String getTemplateId(String sms, String provider) {
    Map<String, Map<String, String>> smsTemplateConfig = SmsTemplateUtil.getSmsTemplateConfigMap();
    Map<String, String> providerTemplateConfig = smsTemplateConfig.get(provider);
    // getSmsTemplateConfigMap() returns an empty map when the smsTemplateConfig row is
    // absent from system_settings, so this lookup is null and entrySet() threw NPE. The
    // caller catches plain Exception and reports "Error in converting providerDetails to
    // string!", which points at serialisation and hides the real cause. Missing config
    // should yield no template id - exactly what "no pattern matched" already yields.
    if (providerTemplateConfig == null || providerTemplateConfig.isEmpty()) {
      return "";
    }
    for (Map.Entry<String, String> entry : providerTemplateConfig.entrySet()) {
      String pattern = entry.getKey().replaceAll("\\$[^ .]+", ".*?");
      if (sms.matches(pattern)) {
        return entry.getValue();
      }
    }
    return "";
  }

  /**
   * Sends an SMS to a single phone number using the default country code.
   *
   * @param phoneNumber The recipient's phone number.
   * @param smsText The SMS content.
   * @param context Request context for logging and tracking.
   * @return true if the SMS was sent successfully, false otherwise.
   */
  boolean send(String phoneNumber, String smsText, RequestContext context);

  /**
   * Sends an SMS to a single phone number with a specific country code.
   *
   * @param phoneNumber The recipient's phone number.
   * @param countryCode The country code to use.
   * @param smsText The SMS content.
   * @param context Request context for logging and tracking.
   * @return true if the SMS was sent successfully, false otherwise.
   */
  boolean send(String phoneNumber, String countryCode, String smsText, RequestContext context);

  /**
   * Sends an SMS to multiple phone numbers.
   *
   * @param phoneNumber The list of recipient phone numbers.
   * @param smsText The SMS content.
   * @param context Request context for logging and tracking.
   * @return true if the SMS was sent successfully, false otherwise.
   */
  boolean send(List<String> phoneNumber, String smsText, RequestContext context);
}
