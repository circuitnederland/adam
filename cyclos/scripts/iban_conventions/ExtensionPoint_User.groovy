import org.cyclos.model.ValidationException
import org.cyclos.model.system.extensionpoints.UserExtensionPointEvent
import org.cyclos.model.users.groups.GroupVO
import org.cyclos.model.users.users.ValidateRegistrationFieldParams

/**
 * Extension point script to ensure the IBAN user profile field complies to our conventions (spaces and uppercase letters).
 */

def usr = scriptHelper.wrap(user)

if (usr.iban == null) {
    // The user does not have the IBAN profile field, so we don't need to check/correct it.
    return
}

def correctedIban = new Utils(binding).ibanByConvention(usr.iban)

if (usr.iban == correctedIban) {
    // The user profile iban was already correct, so return and do nothing.
    return
}

// Update the corrected iban value in the user profile.
usr.iban = correctedIban

// Validate the corrected IBAN value, so we can show a proper VE if the IBAN is not unique anymore after we applied the spacing conventions.

// If we are updating a user, we can use the userService to validate the userDTO.
UserExtensionPointEvent event = binding.event
if (event == UserExtensionPointEvent.UPDATE) {
    def usrDTO = userService.load(user.id)
    return userService.validate(usrDTO) // This returns void, but will throw a ValidationException if the profile contains errors.
}

// If we are creating a new user, there is no userDTO yet. We check if the corrected IBAN value already exists as an IBAN profile field value.
if (event == UserExtensionPointEvent.CREATE) {
    def params = new ValidateRegistrationFieldParams()
    params.group = new GroupVO(user.group.id)
    params.field = 'iban'
    params.value = correctedIban
    def result = userService.validateRegistrationField(params)
    // If the result is not null, the IBAN value is not valid, so throw a VE.
    if (result) {
        throw new ValidationException(result)
    }
}
