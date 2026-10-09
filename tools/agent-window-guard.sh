# agent-window-guard.sh — sourced, never run: `. tools/agent-window-guard.sh; refuse_agent_window <what>` (r237).
#
# The labs' open mode (tools/fog-lab.sh, haze-lab.sh, transfert-lab.sh with no argument, tools/browser-test.sh --open)
# puts Chrome on wayland-0 when DISPLAY and WAYLAND_DISPLAY are both empty, as the board's server needs. In an agent
# session that is Simon's screen: .claude/settings.json's rail (WAYLAND_DISPLAY=no-screen-for-agents) is all that keeps
# the branch from firing, and a session that unsets it would open the window there. So, as gradle/offscreen.gradle
# decides: ATELIER_AGENT set (a board session, or `atelier lab open` asked by one) or CLAUDECODE=1 refuses, exit 2,
# before anything is built. ATELIER_NO_OFFSCREEN=1 lets it through, only when Simon asked to watch. The board's own
# Open button runs with neither (its server's env, launch._spawn adds ATELIER_AGENT only for an agent), and opens.
# tools/agent-screen-check.sh proves both against a sentinel wayland-0.
refuse_agent_window() {
	[ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ] && return 0
	[ -n "${ATELIER_AGENT:-}" ] || [ "${CLAUDECODE:-}" = 1 ] || return 0
	echo "$1: an agent session (ATELIER_AGENT or CLAUDECODE=1) opens no window on Simon's screen: refused." \
		"Use the headless modes, or ATELIER_NO_OFFSCREEN=1 when Simon asked to watch." >&2
	exit 2
}
