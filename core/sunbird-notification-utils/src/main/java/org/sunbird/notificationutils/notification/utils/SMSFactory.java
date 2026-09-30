package org.sunbird.notificationutils.notification.utils;

import org.sunbird.common.ProjectUtil;
import org.sunbird.keys.JsonKey;
import org.sunbird.notificationutils.notification.sms.provider.ISmsProvider;
import org.sunbird.notificationutils.notification.sms.provider.ISmsProviderFactory;
import org.sunbird.notificationutils.notification.sms.providerimpl.Msg91SmsProviderFactory;
import org.sunbird.notificationutils.notification.sms.providerimpl.NICGatewaySmsProviderFactory;

/**
 * Utility factory class that provides the appropriate {@link ISmsProvider} instance based on system
 * configuration.
 */
public class SMSFactory {

  /** The configured SMS provider identifier. */
  public static final String SMS_PROVIDER = ProjectUtil.getConfigValue(JsonKey.SMS_GATEWAY_PROVIDER);

  /**
   * Returns an instance of {@link ISmsProvider} based on the application configuration. Defaults to
   * Msg91 if no matching provider is found.
   *
   * @return An implementation of {@link ISmsProvider}.
   */
  public static ISmsProvider getInstance() {
    ISmsProviderFactory factory;
    if (JsonKey.MSG_91.equalsIgnoreCase(SMS_PROVIDER)) {
      factory = new Msg91SmsProviderFactory();
    } else if (JsonKey.NIC.equalsIgnoreCase(SMS_PROVIDER)) {
      factory = new NICGatewaySmsProviderFactory();
    } else {
      factory = new Msg91SmsProviderFactory();
    }
    return factory.create();
  }
}
