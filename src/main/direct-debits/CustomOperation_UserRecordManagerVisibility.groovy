import org.cyclos.entities.users.UserRecord

/**
 * Code block determining availability of the Custom operation to manage directDebit user records by financial admins.
 */

Map<String, String> scriptParameters = binding.scriptParameters
UserRecord record = binding.record
String action = scriptParameters.action

return new DirectDebits(binding).isActionAvailable(record, action)
