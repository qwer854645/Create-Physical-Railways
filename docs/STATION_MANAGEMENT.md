# Physical train station management

Scope: station information, native editing/assembly boundaries, and reconstruction
after loading. Station schedule delivery and retrieval remain unchanged and blocked
for physical proxies; the actual schedule stays in the primary onboard controls.

## Behavior

- Train identity, name and current station continue to come from the physical backend.
  The native station screen displays the onboard name as read-only and refreshes it
  while open. Change the name using the primary controls.
- Native train name, icon and map-color edit packets cannot mutate the temporary
  physical query proxy. Native train editing remains unchanged.
- Assembly, assembly eligibility and direct disassembly all reject a station with
  a physical train association, including calls without a player. Actual physical
  assembly and removal still use this mod's tools.
- A newly loaded, stopped train checks both actual ends for station alignment. After
  binding, its arrival end stays fixed while parked, even if the reverser changes.
  Candidate endpoints are combined before advancing the six-tick stop debounce.
  Physical speed, position, loaded station, graph and foreign reservation checks
  still apply; no persisted arrival flag substitutes for these checks.
- The client retries graph resolution and station association every ten ticks.
  This covers graph packets arriving after identity packets, unresolved endpoints
  on partially synchronized graphs, and station reservations reset by graph updates.
- Existing identity, controls and schedule persistence formats are unchanged.
  ScheduleController remains responsible for runtime progress restoration.

## Verification and acceptance

Offline: full build/checks, native API/entry-point bytecode contracts, stop/binding
checks, language consistency and compilation of the optional signal fixtures.
These do not prove game UI rendering or a real save/reload cycle.

Manual acceptance (client launch requires an explicit request):

1. Stop at a station, verify its train name, flag and comparator; leave and verify
   the station releases the train. Rename using onboard controls while another
   player keeps the station screen open; verify the displayed name follows.
2. Check that station editing cannot rename the physical train or change temporary
   icon/color state. Try station assembly/disassembly; verify the restriction hint
   and that the physical body remains intact. Repeat normal editing and assembly
   with a native Create train to confirm those actions still work.
3. Park after forward travel, change the reverser, then save/rejoin. Repeat after
   reverse travel and with a coupled consist. Verify the same identity and name,
   correct presence after loading, and release on actual departure.
4. Save/rejoin during travel and during a scheduled station wait. Verify the
   existing running/paused state, instruction and wait progress resume correctly;
   no second native runtime or automatic schedule exchange should occur.
5. Leave/re-enter the station area, reconnect, and edit nearby tracks. Verify station
   and train queries recover after graph synchronization, without stealing a native
   train's occupied station or claiming a station during a fast pass.

No Minecraft client or server was launched for this change. Game acceptance pending.
