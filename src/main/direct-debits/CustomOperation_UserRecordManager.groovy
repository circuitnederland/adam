import org.cyclos.entities.users.UserRecord

/**
  * Custom operation script with Scope 'Record'. Enables financial admins to manage directDebit user records.
  * Financial admins can only apply specific changes, for example to mark a record as failed if the bank 
  * informs us that an incasso has failed. Each of these possible changes has its own custom operation, but they 
  * all share this same script. The 'action' scriptparameter of the custom operation indicates which situation is applicable.
  */

Map<String, Object> formParameters = binding.formParameters
Map<String, String> scriptParameters = binding.scriptParameters
UserRecord record = binding.record
String action = scriptParameters.action

new DirectDebits(binding).updateDirectDebit(record, action, formParameters)

return [
    notification: "De incasso is succesvol gewijzigd.",
    backToRoot: true,
    reRun: true
]
