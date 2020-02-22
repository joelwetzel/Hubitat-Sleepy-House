# Hubitat - Sleepy House
An app for Hubitat to put rooms to sleep.  It does many of the same actions as motion lighting, but it has a different philosophy. It's centered on the idea that at night, rooms should "relax" to a dimmed state and then off, and motion can wake them up.

So, when it's "nighttime":

- If there's no activity in a room, it should tend to go to sleep after a bit: dim down and then go dark.
- If you enter a room, it should wake up in a dimmed state. (So it doesn't blind you.)
- It should make sure that "off" dimmers are preset to a very low level. So that if you enter a room and manually turn a switch on, the light comes on dimmed instead of blinding you with the force of a thousand suns.

### Example Use Cases:
- My kitchen has several dimmers and also several on/off switches. And two motion sensors. At night, if there is no activity, it dims down and then turns everything off. If I come back in to the room, it brings the dimmers back up to the low level, but leaves the on/off switches off.
- My hallway has dimmers, and two motion sensors. At night, if the motion sensors detect no activity for a while, it dims the lights down and then eventually turns them off. However, my wife doesn't like the hallway to come back on automatically, so I have auto-on disabled. But, since it already dimmed the lights before turning them off, that means that if I get up in the middle of the night and manually hit the switch, I know it will come on at a low level and not blind me.

### Installation:
There are two custom apps to install.  Both Sleepy-House.groovy and Sleepy-Room.groovy must be installed.
