# GitHub traffic CLI

Use this tool from the command line to present GitHub Traffic data for your repo in a textual format.
                             
# Usage

> Ensure a JVM 8 or greater is on your execution path.

Use the included `traffic` shell script to run the GitHub traffic CLI tool.

Example:
```
traffic -user joeuser -repo joeswidget -token xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

#### Required parameters:

`-user`: Github user/org name

`-repo`: Github repository name

`-token`: Github authorization token

#### Optional parameters:

`-days`: Number of days to display. Values may range from 1..14. Default is 14.

