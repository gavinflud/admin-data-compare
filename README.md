# Admin Data Delta Tool

This was a small tool quickly built to solve an issue where there was one out-of-date large XML file existed containing
all admin data of a certain type, and another team had generated a new file based off the data in this which was not in
the same order and had modified some of the entries from the original while adding a bunch of new entries.

Since these files were 100k+ lines long, not in a consistent order, and merge tools were not able to tell us what the
other team had changed, this tool was built to parse both XML files in a directory and generate a changelog file
detailing what was changed/added/removed by the newer one, as well as generating a delta XML file containing only the
data that had been changed or added. This delta file could be compared to the other delta files imported since the
original big-bang file was imported, and could be imported with the guarantee that it would only import what the team
had actually added or changed (not reverting any data back to an older value).

To use:

1. Create an import directory and add the two big-bang XML files into it.
2. Create an order.txt file in that directory and specify the two files in order.
3. Compile the application.
4. Run the following:

    java -jar admin-data-compare-\<version\>.jar \<path-to-import-directory\>