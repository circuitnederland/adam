/*
 * Service Interceptor script for users Service.
 * This script adds all profile fields to the users search that need to be returned, also multiselection fields.
 * Use a script parameter to define the fields that should be included.
 *
*/
import org.cyclos.impl.access.SessionData
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.system.ServiceInterceptorContext
import org.cyclos.model.users.users.UserDTO
import org.cyclos.services.users.UserService
import org.cyclos.utils.CustomFieldHelper
import java.util.stream.Collectors

SessionData sessionData = binding.sessionData
ServiceInterceptorContext context = binding.context
ScriptHelper scriptHelper = binding.scriptHelper
Properties scriptParameters = binding.variables.scriptParameters
UserService userService = binding.userService
def allowedFields = scriptParameters['allowedFields']

// Only continue for the targeted user.
if ( scriptParameters['targetUser'] != sessionData.basicUser?.username ) {
    return
}
// Put all customvalues including multiple selection profile fields to the service result.
// But only add allowed fields and skip fields with empty values.
if ( context.success ) {
    context.result
        .stream()
        .forEach { user ->
            UserDTO usrDTO = userService.load(user.id)
            def enrichedCustomValues = scriptHelper.wrap(usrDTO).customValues
                .stream()
                .filter(cv -> allowedFields.contains(cv.field.internalName))
                .filter(cv -> CustomFieldHelper.hasValue(cv))
                .collect(Collectors.toList())
            user.customValues = enrichedCustomValues
        }
}
