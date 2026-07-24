import org.cyclos.entities.users.User
import org.cyclos.entities.users.UserRecord
import org.cyclos.impl.access.SessionData
import org.cyclos.impl.access.SessionHandler
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.users.UserGroupServiceLocal
import org.cyclos.impl.users.UserServiceLocal
import org.cyclos.model.users.groups.BasicGroupVO
import org.cyclos.model.users.groups.ChangeGroupDTO
import org.cyclos.model.users.users.BasicUserVO
import org.cyclos.server.utils.SecureRandomHelper

UserGroupServiceLocal userGroupService = binding.userGroupService
UserServiceLocal userService = binding.userService
ScriptHelper scriptHelper = binding.scriptHelper
Map<String, Object> formParameters = binding.formParameters
User user = formParameters.user

// Determine the new Group.
String groupInternalName = user.group.internalName == 'nww_consumers_card' ?
     'nww_consumers_account' : 'nww_companies_standard'

// Move the user to the new Group.
ChangeGroupDTO changeGroupDto = new ChangeGroupDTO()
changeGroupDto.user = new BasicUserVO(user.id)
changeGroupDto.group = new BasicGroupVO([internalName: groupInternalName])
changeGroupDto.comment = 'Groep automatisch gewijzigd bij toegang betaalnetwerk.'
Long logID = userGroupService.changeGroup(changeGroupDto)

// Set the iban from the idCheck record in the profile field.
UserRecord idCheck = formParameters.idCheck
def idCheckFields = scriptHelper.wrap(idCheck)
def ibanRecord = idCheckFields?.bankAcct
def ibanRecordFields = scriptHelper.wrap(ibanRecord)
def usrDTO = userService.load(user.id)
def usr = scriptHelper.wrap(usrDTO)
usr.iban = ibanRecordFields?.iban

// Put a random secret code in the reference profile field of the user.
usr.reference = SecureRandomHelper.randomNumeric(4)

// Save the user.
userService.save(usrDTO)

// Log the user out.
SessionHandler sessionHandler = binding.sessionHandler
SessionData sessionData = binding.sessionData
sessionHandler.remove(sessionData.session)

// Try to return to the homepage.
return sessionData.configuration.fullUrl
