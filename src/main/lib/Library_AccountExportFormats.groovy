import org.cyclos.entities.banking.Account
import org.cyclos.entities.users.User
import org.cyclos.impl.access.SessionData
import org.cyclos.impl.banking.AccountHistoryEntry
import org.cyclos.impl.banking.AccountServiceLocal
import org.cyclos.impl.system.ScriptHelper
import org.cyclos.impl.utils.conversion.ConversionHandler
import org.cyclos.model.banking.accounts.AccountHistoryQuery
import org.cyclos.server.utils.Now
import org.cyclos.utils.StringHelper

import groovy.transform.TypeChecked
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil

import java.text.SimpleDateFormat

// Don't type check the trait because the checker won't find the constants in the classes using the trait.
trait ExportFormatting {
    String currencyCode
    String iban
    ScriptHelper scriptHelper
    Iterator data
    SimpleDateFormat dateFormat
    SimpleDateFormat entryDateFormat
    Date beginDate
    Date endDate
    BigDecimal beginBalance
    BigDecimal endBalance

    void setup (Binding binding) {
        def vars = binding.variables
        data = vars.data as Iterator
        scriptHelper = vars.scriptHelper as ScriptHelper
        def timeZone = vars.sessionData.configuration.timeZone
        dateFormat = new SimpleDateFormat(this.DATE_FORMAT)
        dateFormat.timeZone = timeZone
        entryDateFormat = new SimpleDateFormat(this.ENTRY_DATE_FORMAT)
        entryDateFormat.timeZone = timeZone
        def conversionHandler = vars.conversionHandler as ConversionHandler
        AccountHistoryQuery query = vars.query as AccountHistoryQuery
        Account account = conversionHandler.convert(Account, query.account)
        Map<String, String> scriptParameters = binding.scriptParameters
        currencyCode = scriptParameters.currencyCode
        iban = scriptParameters["iban.${account.type.internalName}"]
        Date now = Now.date()
        beginDate = conversionHandler.toDate(query.period?.begin) ?: account.creationDate
        endDate = conversionHandler.toDate(query.period?.end) ?: now
        if (endDate.after(now)) {
            endDate = now
        }
        AccountServiceLocal accountService = vars.accountService as AccountServiceLocal
        beginBalance = accountService.getBalance(account, beginDate)
        endBalance = accountService.getBalance(account, endDate)
    }

    String formatAmount(BigDecimal amount) {
        def amt = amount.abs().toPlainString()
        switch(this.DECIMAL_SEPARATOR) {
            case ',':
                amt = amt.replace('.', ',')
                break
            case '.':
                amt = amt.replace(',', '.')
                break
        }
        return amt
    }

    String formatSignal(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) > 0 ? this.CREDIT_SIGNAL : this.DEBIT_SIGNAL
    }


    String formatOwner(Account account) {
        String text
        if (account.owner instanceof User) {
            text = account.owner.username
        } else {
            text = account.type.name
        }
        return formatText(text)
    }

    String formatDescription(AccountHistoryEntry entry) {
        def description = entry.transaction?.description ?: entry.type.valueForEmptyDescription
        return formatText(description)
    }

    String formatText(text) {
        // First replace line breaks or multiple spaces by a single space, trimming to 60 chars
        text = (text ?: '').replaceAll("[\n|\r]+", " ")
        text = text.replaceAll("\\s+", " ")
        text = StringHelper.trim(StringHelper.truncate(text, 60))
        // Second make sure that no special characters are used
        text = StringHelper.asciiOnly(StringHelper.unaccent(text))
        // Finally make sure that no colon character is used, this might mess up the mt940 file
        return text.replaceAll('\\:', ' ')
    }

    abstract String generateContents()

}

@TypeChecked
class Mt940 implements ExportFormatting {
    public static final String DATE_FORMAT = "yyMMdd"
    public static final String ENTRY_DATE_FORMAT = "yyMMdd"
    public static final String CREDIT_SIGNAL = "C"
    public static final String DEBIT_SIGNAL = "D"
    public static final char DECIMAL_SEPARATOR = ','

    Mt940(Binding binding) {
        setup(binding)
    }

    String generateContents() {
        def dateBegin = dateFormat.format(beginDate)
        def dateEnd = dateFormat.format(endDate)
        def signalBegin = formatSignal(beginBalance)
        def amountBegin = formatAmount(beginBalance)
        def signalEnd = formatSignal(endBalance)
        def amountEnd = formatAmount(endBalance)
        StringBuilder out = new StringBuilder(""":20:CN${dateEnd}
:25:${iban}
:28:000
:60F:${signalBegin}${dateBegin}${currencyCode}${amountBegin}
""")

        scriptHelper.processBatch(data) { AccountHistoryEntry entry ->
            def date = entryDateFormat.format(entry.date)
            def amount = formatAmount(entry.amount)
            def signal = formatSignal(entry.amount)
            def fromTo = formatOwner(entry.relatedAccount)
            def description = formatDescription(entry)
            out << ":61:${date}${signal}${amount}NOV NONREF\n"
            out << ":86:${fromTo} > ${description}\n"
        }

        out << ":62F:${signalEnd}${dateEnd}${currencyCode}${amountEnd}"

        return out.toString()
    }
}

// Note: don't typeCheck this class because the builder methods are unknown by the compiler.
class Camt053 implements ExportFormatting {
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
    public static final String ENTRY_DATE_FORMAT = "yyyy-MM-dd"
    public static final String CREDIT_SIGNAL = "CRDT"
    public static final String DEBIT_SIGNAL = "DBIT"
    public static final String MSG_ID_PREFIX = "CAMT053"
    public static final String ISSR_CODE = "MOPA"
    public static final char DECIMAL_SEPARATOR = '.'

    Camt053(Binding binding) {
        setup(binding)
    }

    String generateContents() {
        // Prepare the xml builder and bind the root tag to it. This will build up the xml hierarchy.
        def builder = new StreamingMarkupBuilder(useDoubleQuotes: true)
        builder.encoding = "utf-8"
        Writable xml = builder.bind(document)

        // Use XmlUtils serialize to turn the XML into a pretty string with newlines and return this.
        return XmlUtil.serialize(xml)
    }

    Closure document = { b ->
        def millis = String.valueOf(System.currentTimeMillis()).substring(0, 12)
        def msgId = MSG_ID_PREFIX + ISSR_CODE + millis
        def now = Now.date()
        def msgCreation = dateFormat.format(now)
        def stmtId = MSG_ID_PREFIX + millis + "00001"
        def dateBegin = entryDateFormat.format(beginDate)
        def dateEnd = entryDateFormat.format(endDate)
        def amountBegin = formatAmount(beginBalance)
        def amountEnd = formatAmount(endBalance)
        def signalBegin = formatSignal(beginBalance)
        def signalEnd = formatSignal(endBalance)

        b.mkp.xmlDeclaration()
        b.mkp.declareNamespace(
            "": "urn:iso:std:iso:20022:tech:xsd:camt.053.001.02",
            "xsi": "http://www.w3.org/2001/XMLSchema-instance"
        )
        b.Document() {
            b.BkToCstmrStmt() {
                b.GrpHdr() {
                    MsgId(msgId)
                    CreDtTm(msgCreation)
                }
                Stmt() {
                    Id(stmtId)
                    CreDtTm(msgCreation)
                    Acct() {
                        Id() {
                            IBAN(iban)
                        }
                        Ccy(currencyCode)
                    }
                    Bal(){
                        Tp(){
                            CdOrPrtry(){
                                Cd('OPBD')
                            }
                        }
                        Amt(Ccy: currencyCode, amountBegin)
                        CdtDbtInd(signalBegin)
                        Dt() {
                            Dt(dateBegin)
                        }
                    }
                    Bal(){
                        Tp(){
                            CdOrPrtry(){
                                Cd('CLBD')
                            }
                        }
                        Amt(Ccy: currencyCode, amountEnd)
                        CdtDbtInd(signalEnd)
                        Dt() {
                            Dt(dateEnd)
                        }
                    }
                    data.each {
                        this.entryInformation(b, it)
                    }
                }
            }
        }
    }

    Closure entryInformation = { b, trx ->
        def amountTrx = formatAmount(trx.amount)
        def signalTrx = formatSignal(trx.amount)
        def dateTrx = entryDateFormat.format(trx.date)
        def codeTrx = trx.transactionNumber
        b.Ntry() {
            Amt(Ccy: currencyCode, amountTrx)
            CdtDbtInd(signalTrx)
            Sts('BOOK')
            BookgDt(){
                Dt(dateTrx)
            }
            ValDt(){
                Dt(dateTrx)
            }
            BkTxCd(){
                Prtry(){
                    Cd(codeTrx)
                }
            }
            NtryDtls(){
                TxDtls(){
                    RltdPties(){
                        Cdtr(){
                            Nm(formatOwner(trx.relatedAccount))
                        }
                    }
                    RmtInf(){
                        Ustrd(formatText(trx.description))
                    }
                }
            }
        }
    }
}
