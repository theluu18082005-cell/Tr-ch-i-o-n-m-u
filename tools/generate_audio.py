"""Original ColorDuel sounds. Python standard library only, deterministic PCM WAV.
Run from the repository: python tools/generate_audio.py
No runtime download, dependency or synthesis is required by the Java client.
"""
import math
from pathlib import Path
import struct
import wave

RATE = 44100
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/audio"
OUT.mkdir(parents=True, exist_ok=True)

def save(name, samples):
    pcm = b"".join(struct.pack("<h", round(max(-.34, min(.34, x))*32767)) for x in samples)
    with wave.open(str(OUT / (name + ".wav")), "wb") as f:
        f.setnchannels(1); f.setsampwidth(2); f.setframerate(RATE); f.writeframes(pcm)

def phrase(name, notes, length, level=.25):
    samples=[]
    for i in range(round(length*RATE)):
        t=i/RATE; value=0
        for start, duration, frequency in notes:
            age=t-start
            if 0 <= age < duration:
                attack=min(1,age/.008); release=min(1,(duration-age)/.035)
                envelope=attack*release*math.exp(-age*2.2/max(duration,.1))
                value += level*envelope*(math.sin(2*math.pi*frequency*age)+.12*math.sin(2*math.pi*frequency*2*age))
        samples.append(value)
    save(name,samples)

phrase("click",[(0,.065,520)],.075,.19)
phrase("orb",[(0,.11,660),(.035,.10,990)],.15,.18)
phrase("swap",[(0,.08,330),(.055,.10,550)],.17,.19)
phrase("submit",[(0,.09,440),(.075,.12,660),(.15,.13,880)],.30,.21)
phrase("invite",[(0,.18,523.25),(.21,.22,783.99)],.46,.23)
phrase("accept",[(0,.10,392),(.08,.13,523.25),(.16,.15,659.25)],.34,.21)
phrase("reject",[(0,.13,440),(.10,.17,293.66)],.30,.19)
phrase("turn",[(0,.13,440),(.12,.15,659.25),(.24,.16,880)],.43,.20)
phrase("warning",[(0,.10,440)],.13,.19)
phrase("timeout",[(0,.14,392),(.12,.16,261.63),(.27,.20,196)],.50,.22)
phrase("victory",[(0,.19,392),(.15,.20,523.25),(.30,.22,659.25),(.48,.30,783.99),(.74,.40,1046.5)],1.18,.24)
phrase("defeat",[(0,.26,329.63),(.23,.26,261.63),(.46,.38,196)],.88,.21)
phrase("peer-left",[(0,.15,523.25),(.14,.22,349.23)],.40,.19)
# Eight-second music bed: audible midrange pad and restrained arcade arpeggio.
# Every note finishes before its wrapped period; the pad frequencies close at eight seconds.
melody=[440,554.365,659.255,880,659.255,554.365,493.883,659.255]*2
ambient=[]
for i in range(8*RATE):
    t=i/RATE
    value=sum(.028*math.sin(2*math.pi*f*t+j*.7)*(.7+.3*math.cos(2*math.pi*t/8+j))
        for j,f in enumerate([220,330,440,550]))
    for note,f in enumerate(melody):
        age=(t-note*.5)%8
        if age < .38:
            envelope=min(1,age/.012)*min(1,(.38-age)/.05)*math.exp(-age*5)
            value += .065*envelope*math.sin(2*math.pi*f*age)
    ambient.append(value)
save("ambient",ambient)
# Battle: 120 BPM, sixteen beats per seamless eight-second loop. Rounded sine bass,
# a soft kick/backbeat and eighth-note accents give motion without sharp saw waves.
# All voices have finite attack/release envelopes and end before the loop boundary.
battle=[]
bass=[164.814,164.814,196,164.814,146.832,146.832,196,146.832,
      130.813,130.813,164.814,196,146.832,146.832,196,246.942]
lead=[659.256,493.884,392,493.884,659.256,784,659.256,493.884,
      587.328,440,392,440,587.328,784,587.328,440,
      523.252,392,329.628,392,523.252,659.256,784,659.256,
      587.328,440,392,440,587.328,659.256,784,987.768]
def envelope(age,duration,attack,decay):
    return min(1,age/attack)*min(1,max(0,(duration-age)/.035))*math.exp(-age*decay) if age<duration else 0
for i in range(8*RATE):
    t=i/RATE; beat=int(t/.5); age=t-beat*.5
    f=bass[beat]
    value=.075*envelope(age,.32,.012,3)*(math.sin(2*math.pi*f*age)+.15*math.sin(4*math.pi*f*age))
    # A falling sine kick, with continuous phase, on each quarter note.
    value+=.13*envelope(age,.20,.004,18)*math.sin(2*math.pi*(48*age+80*(1-math.exp(-age*35))/35))
    if beat%2==1:
        noise=sum(math.sin(2*math.pi*h*age) for h in (947,1423,2137))/3
        value+=.035*envelope(age,.15,.004,20)*noise
    note=int(t/.25); note_age=t-note*.25
    value+=.065*envelope(note_age,.19,.009,9)*math.sin(2*math.pi*lead[note]*note_age)
    value+=.012*envelope(note_age,.055,.003,45)*(math.sin(2*math.pi*3109*note_age)+math.sin(2*math.pi*4211*note_age))/2
    battle.append(value)
save("battle",battle)
print("Generated 15 original WAV assets: 13 effects and 2 scene music loops")
