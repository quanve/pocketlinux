#!/system/bin/sh
TEXT="${1:-hello}"
DELAY="${2:-3}"
G=/config/usb_gadget/g1
HIDF=$G/functions/hid.usb0
LOG=/data/local/tmp/usb-hid.log
exec >>"$LOG" 2>&1
echo "=== $(date) text=[$TEXT] delay=$DELAY"
cleanup() {
    echo "" > "$G/UDC" 2>/dev/null
    rm -f "$G/configs/b.1/f5"
    rmdir "$HIDF" 2>/dev/null
    setprop sys.usb.config adb
    echo "[hid] restored adb"
}
trap 'cleanup; exit 1' INT TERM HUP
[ -e /dev/hidg0 ] && { echo "[hid] /dev/hidg0 already exists"; exit 1; }
setprop sys.usb.config none
i=0
while [ -n "$(cat "$G/UDC" 2>/dev/null)" ] && [ $i -lt 10 ]; do sleep 0.5; i=$((i+1)); done
echo "[hid] udc=[$(cat "$G/UDC" 2>/dev/null)]"
mkdir -p "$HIDF" || { echo "[hid] mkdir failed"; cleanup; exit 1; }
echo 1 > "$HIDF/protocol"
echo 1 > "$HIDF/subclass"
echo 8 > "$HIDF/report_length"
printf '\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x03\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x03\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0' > "$HIDF/report_desc"
cd "$G" || { cleanup; exit 1; }
ln -s functions/hid.usb0 configs/b.1/f5 || { echo "[hid] symlink failed"; cleanup; exit 1; }
echo musb-hdrc.1.auto > "$G/UDC" || { echo "[hid] bind failed"; cleanup; exit 1; }
sleep 2
[ -e /dev/hidg0 ] || { echo "[hid] no /dev/hidg0"; cleanup; exit 1; }
echo "[hid] bound, waiting ${DELAY}s"
sleep "$DELAY"
send_report() {
    _mod=$1; _code=$2
    _rep=$(printf '\\%03o\\%03o\\%03o\\%03o\\%03o\\%03o\\%03o\\%03o' "$_mod" 0 0 "$_code" 0 0 0 0)
    printf "$_rep" > /dev/hidg0
    sleep 0.04
    printf '\0\0\0\0\0\0\0\0' > /dev/hidg0
    sleep 0.04
}
char_key() {
    case "$1" in
        a) echo "0 4";; b) echo "0 5";; c) echo "0 6";; d) echo "0 7";; e) echo "0 8";;
        f) echo "0 9";; g) echo "0 10";; h) echo "0 11";; i) echo "0 12";; j) echo "0 13";;
        k) echo "0 14";; l) echo "0 15";; m) echo "0 16";; n) echo "0 17";; o) echo "0 18";;
        p) echo "0 19";; q) echo "0 20";; r) echo "0 21";; s) echo "0 22";; t) echo "0 23";;
        u) echo "0 24";; v) echo "0 25";; w) echo "0 26";; x) echo "0 27";; y) echo "0 28";; z) echo "0 29";;
        A) echo "2 4";; B) echo "2 5";; C) echo "2 6";; D) echo "2 7";; E) echo "2 8";;
        F) echo "2 9";; G) echo "2 10";; H) echo "2 11";; I) echo "2 12";; J) echo "2 13";;
        K) echo "2 14";; L) echo "2 15";; M) echo "2 16";; N) echo "2 17";; O) echo "2 18";;
        P) echo "2 19";; Q) echo "2 20";; R) echo "2 21";; S) echo "2 22";; T) echo "2 23";;
        U) echo "2 24";; V) echo "2 25";; W) echo "2 26";; X) echo "2 27";; Y) echo "2 28";; Z) echo "2 29";;
        1) echo "0 30";; 2) echo "0 31";; 3) echo "0 32";; 4) echo "0 33";; 5) echo "0 34";;
        6) echo "0 35";; 7) echo "0 36";; 8) echo "0 37";; 9) echo "0 38";; 0) echo "0 39";;
        '!') echo "2 30";; '@') echo "2 31";; '#') echo "2 32";; '$') echo "2 33";; '%') echo "2 34";;
        '^') echo "2 35";; '&') echo "2 36";; '*') echo "2 37";; '(') echo "2 38";; ')') echo "2 39";;
        ' ') echo "0 44";; '-') echo "0 45";; '_') echo "2 45";; '=') echo "0 46";; '+') echo "2 46";;
        '[') echo "0 47";; '{') echo "2 47";; ']') echo "0 48";; '}') echo "2 48";;
        '\') echo "0 49";; '|') echo "2 49";; ';') echo "0 51";; ':') echo "2 51";;
        "'") echo "0 52";; '"') echo "2 52";; '`') echo "0 53";; '~') echo "2 53";;
        ',') echo "0 54";; '<') echo "2 54";; '.') echo "0 55";; '>') echo "2 55";;
        '/') echo "0 56";; '?') echo "2 56";;
        *) echo "";;
    esac
}
i=0
n=${#TEXT}
while [ $i -lt $n ]; do
    ch=$(printf '%s' "$TEXT" | cut -c$((i+1)))
    pair=$(char_key "$ch")
    if [ -n "$pair" ]; then
        send_report ${pair% *} ${pair#* }
    else
        echo "[hid] skip unsupported char [$ch]"
    fi
    i=$((i+1))
done
echo "[hid] typed $n chars"
sleep 1
cleanup
