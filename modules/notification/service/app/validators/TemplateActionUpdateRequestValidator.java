package validators;

import org.sunbird.keys.JsonKey;
import org.sunbird.exception.BaseException;
import org.sunbird.message.IResponseMessage;
import org.sunbird.message.ResponseCode;
import org.sunbird.request.Request;
import org.sunbird.response.Response;
import org.sunbird.logging.LoggerUtil;
import utils.ValidationUtil;

import java.util.Arrays;

public class TemplateActionUpdateRequestValidator implements RequestValidatorFunction<Request, Response> {
    private static LoggerUtil logger = new LoggerUtil(TemplateActionUpdateRequestValidator.class);
    @Override
    public Response apply(Request request) throws BaseException {
        try{
            ValidationUtil.validateRequestObject(request);
            ValidationUtil.validateMandatoryParamsWithType(request.getRequest(), Arrays.asList(JsonKey.TEMPLATE_ID,JsonKey.ACTION),String.class,true,JsonKey.REQUEST,request.getContext());
        }catch (Exception ex){
            logger.error(request.getContext(),"Validation error",ex);
            throw new BaseException(IResponseMessage.Key.INVALID_REQUESTED_DATA, ex.getMessage(), ResponseCode.BAD_REQUEST.getCode());

        }
        return null;
    }
}
