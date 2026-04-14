package org.sunbird.util;

import java.io.StringWriter;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.sunbird.dao.notification.EmailTemplateDao;
import org.sunbird.dao.notification.impl.EmailTemplateDaoImpl;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.request.RequestContext;
import org.sunbird.common.ProjectUtil;

public class SMSTemplateProvider {
  private static final LoggerUtil logger = new LoggerUtil(SMSTemplateProvider.class);
  public static final String SMS_PROVIDER =
      ProjectUtil.getConfigValue(JsonKey.SMS_GATEWAY_PROVIDER);
  private static final EmailTemplateDao emailTemplateDao = EmailTemplateDaoImpl.getInstance();

  private SMSTemplateProvider() {}

  private static String getTemplate(String templateId, RequestContext context) {
    String defaultTemplate = templateId;
    if (StringUtils.isNotBlank(templateId) && JsonKey.NIC.equalsIgnoreCase(SMS_PROVIDER)) {
      defaultTemplate = templateId + "_nic";
    }
    return emailTemplateDao.getTemplate(defaultTemplate, context);
  }

  public static String getSMSBody(
      String smsTemplate, Map<String, String> templateConfig, RequestContext requestContext) {
    try {
      String template = getTemplate(smsTemplate, requestContext);
      if (StringUtils.isBlank(template)) {
        logger.info(requestContext, "SMSTemplateProvider:getSMSBody: Template not found: " + smsTemplate);
        return "";
      }
      VelocityContext context = new VelocityContext();
      if (templateConfig != null) {
        templateConfig.forEach(context::put);
      }
      StringWriter writer = new StringWriter();
      Velocity.evaluate(context, writer, "SMSBody", template);
      return writer.toString();
    } catch (Exception ex) {
      logger.error("Exception occurred while formatting SMS ", ex);
    }
    return "";
  }
}
