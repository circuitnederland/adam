Ensure valid and unique IBANs
========

The IBAN profilefield is set to Unique, but without any other measures, users could still enter a non-unique IBAN if they use a different pattern (more or no spaces, lowercase/uppercase).
To ensure each IBAN entered is realy unique, we use an Extension point on the User Create and Change Events, which forces our convention pattern to the IBAN entered.

Also, we set a Custom Field Validation on the IBAN user profilefield, to ensure it is a valid IBAN.

# Scripts

## Extension point script

- Type: Extension point
- Name: ensure IBAN Conventions
- Run with all permissions: No
- Included libraries: utils Library*
- Script code executed when the data is validated, but not yet saved: iban_conventions/ExtensionPoint_user.groovy

## Custom field validation script

- Type: Custom field validation
- Name: check IBAN
- Run with all permissions: No
- Included libraries: utils Library*
- Script code: iban_conventions/CustomFieldValidation_Iban.groovy

*) If the utils Library does not yet exist, create a Library script called 'utils Library', with the Library_Utils.groovy contents.

# Extension point

Create an Extension point of type User:

- Name: ensure IBAN conventions
- Groups: select the Amsterdam Nieuw-West Group set
- Events: Create and Update
- Script: ensure IBAN Conventions

# Profile field validation

In the IBAN user Profile field:
- Min / max length: remove
- Validation script: check IBAN
