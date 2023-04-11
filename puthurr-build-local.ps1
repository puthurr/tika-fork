# Build scripts for our own Tika version
# one by one
Push-Location tika-core
mvn clean install -DskipTests -Dossindex.skip
Pop-Location

Push-Location tika-parsers/tika-parsers-standard
mvn clean install -DskipTests -Dossindex.skip
Pop-Location

Push-Location tika-parsers/tika-parsers-standard-package
mvn clean install -DskipTests -Dossindex.skip
Pop-Location

Push-Location tika-server
mvn clean install -DskipTests -Dossindex.skip
Pop-Location

# on the root directory, all at once
mvn clean install -DskipTests -Dossindex.skip
