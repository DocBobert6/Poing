# Link self-link spike — verdict: NOT FEASIBLE through the libretro API
#
# Question (PLAN.md §9): can two mGBA instances be linked inside one
# phone — trading / battling with yourself — through the frontend's
# libretro bridge, without forking the core?
#
# Verdict: NO. Self-link is not achievable through the public
# libretro API at the pinned mGBA commit (7a12d6d4). It does not
# ship in v1, and it does not block v1. Phone-to-phone link
# (Phase 7) needs the same core-level work, so this verdict shapes
# that phase too.
#
# ## Evidence (all checked against the pinned tree / the verified .so)
#
# 1. One core instance, by construction. `src/platform/libretro/
#    libretro.c:86` declares a single file-scope global:
#    `static struct mCore* core;`. `retro_load_game` loads into
#    that one core; there is no second-core handle anywhere in the
#    libretro API. A frontend cannot hold two loaded games at once.
#
# 2. Nothing but retro_* is exported. `src/platform/libretro/
#    link.T` is `{ global: retro_*; local: *; }`, and `llvm-nm -D`
#    on the CI-built `mgba_libretro.so` (6,258,632 bytes, verified
#    in Phase 1) shows exactly 25 `T` symbols, all `retro_*`.
#    No SIO / link symbol escapes the .so.
#
# 3. The link implementation is internal core API.
#    `src/gb/sio/lockstep.c` implements `GBSIOLockstep` /
#    `GBSIOLockstepNode` — the lockstep link driver that mGBA's
#    own frontends use to wire two cores together. It is a
#    `struct GBSIODriver` on the core's internal `mCore` API; the
#    libretro layer never exposes it, and `libretro.c` contains no
#    "cable" / link environment command at all (grep: zero hits).
#    The frontend has no handle to attach a driver to.
#
# 4. `retro_set_controller_port_device` is a no-op in this core
#    (`libretro.c:2241` — `UNUSED(port); UNUSED(device);`), so
#    there is no port-device route to a link device either.
#
# 5. dlopen tricks do not help. Loading a second copy of the .so
#    under a different filename would give a second core, but with
#    no exported SIO API the two cores still could not exchange a
#    byte — and the lockstep protocol needs both cores' timing
#    events wired together, which only the core's internal driver
#    can do.
#
# ## What would be needed instead
#
# A core-side change: expose the SIO driver (or a link API) from
# the libretro layer — e.g. a `retro_link_*` extension, or the
# lockstep node driven through a frontend-supplied transport.
# That is a fork of the core, not a frontend change, and it is
# Phase 7's decision, not Phase 2's. The phone-to-phone link will
# need the same work: the lockstep driver plus a network transport
# between two phones' cores.
#
# ## What was NOT tried (and why)
#
# No game was booted for this spike — there is nothing to boot
# against: the API gap above is visible statically, and booting a
# game would not create a second core or a link handle. The
# verdict rests on the symbol table and the source, which is the
# whole of the evidence the question turns on.
