#!/usr/bin/env bash
# BOOSTLAB gateway bootstrap for a fresh Ubuntu 24.04 VPS.
# Run as root on the intended VPS. Installs WireGuard, forwarding and NAT.
set -Eeuo pipefail
if [[ "${EUID}" -ne 0 ]]; then echo "Run as root" >&2; exit 1; fi
if [[ -e /etc/wireguard/wg0.conf ]]; then
  echo "Existing /etc/wireguard/wg0.conf found; refusing to overwrite." >&2
  exit 1
fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y wireguard wireguard-tools iptables python3 ca-certificates
install -d -m 0700 /etc/wireguard
umask 077
wg genkey > /etc/wireguard/server.key
wg pubkey < /etc/wireguard/server.key > /etc/wireguard/server.pub
WAN_IF=$(ip -4 route show default | awk 'NR==1 {for (i=1;i<=NF;i++) if ($i=="dev") {print $(i+1); exit}}')
if [[ -z "$WAN_IF" || ! "$WAN_IF" =~ ^[a-zA-Z0-9_.:-]+$ ]]; then
  echo "Could not identify WAN interface" >&2; exit 1
fi
cat > /etc/wireguard/wg0.conf <<EOF
[Interface]
Address = 10.77.0.1/16
ListenPort = 51820
PrivateKey = $(cat /etc/wireguard/server.key)
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -A FORWARD -o wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -s 10.77.0.0/16 -o $WAN_IF -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -D FORWARD -o wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -s 10.77.0.0/16 -o $WAN_IF -j MASQUERADE
EOF
chmod 0600 /etc/wireguard/wg0.conf /etc/wireguard/server.key
cat > /etc/sysctl.d/90-boostlab-forward.conf <<EOF
net.ipv4.ip_forward=1
EOF
sysctl --system >/dev/null
systemctl enable --now wg-quick@wg0
echo
echo "BOOSTLAB gateway installed"
echo "Server public key: $(cat /etc/wireguard/server.pub)"
echo "Listening: UDP 51820"
wg show wg0
echo "IMPORTANT: allow UDP 51820 in your VPS provider firewall."
echo "No clients have been enrolled yet."
