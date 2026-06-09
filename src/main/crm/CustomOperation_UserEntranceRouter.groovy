import org.cyclos.entities.system.CustomOperation
import org.cyclos.entities.users.User
import org.cyclos.impl.system.ScriptHelper

Utils utils = new Utils(binding)
CRM crm = new CRM(binding)
ScriptHelper scriptHelper = binding.scriptHelper
User user = binding.user
CustomOperation operation = binding.customOperation
Boolean showOpenAccount = false
Boolean showManualTransfer = false
Boolean showEMandate = false
String html

def idCheck = crm.getIDCheck(user)
if (idCheck) {
    // The identity of the user has been checked. We can let them enter.
    showOpenAccount = true
    html = utils.dynamicMessage("entrIDCheckedMsg")
} else {
    showManualTransfer = true
    showEMandate = true
    html = utils.dynamicMessage("entrStartMsg")
}
def actions = [
        openAccount: [
            parameters: [
                user: user.id,
                idCheck: idCheck
            ],
            enabled: showOpenAccount
        ],
        manualTransferEntrance: [
            enabled: showManualTransfer
        ],
        createEMandate: [
            parameters: [
                user: user.id
            ],
            enabled: showEMandate
        ]
    ]
// Only use actions that actually exist as internal actions on the router.
def actualActions = actions.findAll { key, action -> 
    operation.actions.find {
        it.actionOperation.internalName == key
    }
}

return [
    content: html,
    actions: actualActions
]