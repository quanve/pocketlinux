#!/data/data/com.termux/files/usr/bin/python3
"""Fast HID report loop; gadget is prepared by hid-daemon.sh."""
import os, sys, time

def main():
    kbd_fd = os.open("/dev/hidg0", os.O_WRONLY)
    mouse_fd = os.open("/dev/hidg1", os.O_WRONLY)
    zeros8 = bytes(8)
    zeros4 = bytes(4)
    stdin = sys.stdin.buffer
    while True:
        line = stdin.readline()
        if not line:
            break
        parts = line.split()
        if not parts:
            continue
        cmd = parts[0]
        try:
            if cmd == b"k":
                mod, key = int(parts[1]), int(parts[2])
                os.write(kbd_fd, bytes((mod & 255, 0, 0, key & 255, 0, 0, 0, 0)))
                time.sleep(0.001)
                os.write(kbd_fd, zeros8)
                time.sleep(0.001)
            elif cmd == b"kd":
                mod, key = int(parts[1]), int(parts[2])
                os.write(kbd_fd, bytes((mod & 255, 0, 0, key & 255, 0, 0, 0, 0)))
            elif cmd == b"ku":
                os.write(kbd_fd, zeros8)
            elif cmd == b"c":
                mask = int(parts[1])
                os.write(mouse_fd, bytes((mask & 255, 0, 0, 0)))
                time.sleep(0.03)
                os.write(mouse_fd, zeros4)
            elif cmd == b"m":
                mask, dx, dy, wheel = (int(parts[i]) for i in range(1, 5))
                os.write(mouse_fd, bytes((mask & 255, dx & 255, dy & 255, wheel & 255)))
            elif cmd == b"quit":
                break
        except (IndexError, ValueError):
            continue
    os.close(kbd_fd)
    os.close(mouse_fd)

if __name__ == "__main__":
    main()
