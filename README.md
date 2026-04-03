# stdout2ps 

Takes stdout or an input file and generates PostScript including
colors. It compiles easily to native code using graalvm native-image.

For an exmaple of use to turn ANSI-colored script output to a PDF:

    sslscan www.google.com | ./chiralsoftware.stdout2pdf.stdout2ps --header=SslScanGoogle | ps2pdf - > ssl-scan.pdf

# stdout2pdf

Take stdout and turn it into a PDF. This supports ANSI color and fonts. It allows setting
page sizes and margins. It is designed to be compiled to native with graalvm

# checkit

This is for checking config files for presence:

    checkit --file /etc/ssh/sshd_config \
    --grep '^PasswordAuthentication no$' \
    --present 'password auth disabled' \
    --empty 'WARN: password auth still enabled'
