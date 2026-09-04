import org.cyclos.entities.banking.Transaction
import org.cyclos.entities.users.User
import org.cyclos.model.banking.accounts.AccountOwner
import org.cyclos.model.users.records.RecordDataParams
import org.cyclos.model.users.records.UserRecordDTO
import org.cyclos.model.users.recordtypes.RecordTypeVO
import org.cyclos.model.users.users.UserLocatorVO

/**
 * Transaction extension point script to create a new directDebit user record whenever a 
 * directDebit transaction is made from system debit to a member directDebitAccount.
 * Code block: 'Script code executed when the data is saved'.
 * Extension point on Transaction, Transfer type: directDebit, Event: Confirm.
 */

AccountOwner user = binding.toOwner
Transaction transaction = binding.transaction

if (!(user instanceof User)) {
    // The toOwner should be a member. If it is not, something is wrong, so stop.
    return
}

// Create the user record after the transaction is committed, to make sure the record is only created if the transaction is actually there.
// Note: use addOnCommitTransactional instead of addOnCommit, because we need a new database transaction to save the user record.
scriptHelper.addOnCommitTransactional {
    RecordDataParams params = new RecordDataParams(
        user: new UserLocatorVO(id: user.id),
        recordType: new RecordTypeVO(internalName: 'directDebit')
    )
    UserRecordDTO record = recordService.getDataForNew(params).dto
    def fields = scriptHelper.wrap(record)
    fields.transaction = transaction
    recordService.save(record)
}
