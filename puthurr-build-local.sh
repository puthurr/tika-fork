# Build scripts for our own Tika version
# one by one
cd tika-core
mvn clean install -DskipTests -Dossindex.skip
cd ..

cd tika-parsers/tika-parsers-standard
mvn clean install -DskipTests -Dossindex.skip
cd ..

cd tika-parsers/tika-parsers-standard-package
mvn clean install -DskipTests -Dossindex.skip
cd ..

cd tika-server
mvn clean install -DskipTests -Dossindex.skip
cd ..

# on the root directory, all at once
mvn clean install -DskipTests -Dossindex.skip

