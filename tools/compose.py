"""Original music for Desk Racer LAN (composed procedurally, released CC0 by the project).
Renders seamless loops + jingles to OGG.  python3 compose.py <outdir>
"""
import sys, os, numpy as np, soundfile as sf

SR = 44100
rng = np.random.default_rng(7)
NOTE = {n: i for i, n in enumerate(['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'])}

def hz(name):
    # e.g. 'A4', 'C#3'
    n, o = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTE[n] + 12 * (o + 1) - 69) / 12)

def env(n, a, d, s, r, sus_len):
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    sus = max(int(sus_len * SR) - a - d, 0)
    e = np.concatenate([np.linspace(0, 1, max(a, 1)), np.linspace(1, s, max(d, 1)), np.full(sus, s), np.linspace(s, 0, max(r, 1))])
    return e[:n] if len(e) >= n else np.pad(e, (0, n - len(e)))

def osc(kind, f, n, detune=0.0, pw=0.5):
    t = np.arange(n) / SR
    ph = (f * (1 + detune)) * t
    if kind == 'saw':   return 2 * (ph % 1) - 1
    if kind == 'sq':    return np.where((ph % 1) < pw, 1.0, -1.0)
    if kind == 'tri':   return 2 * np.abs(2 * (ph % 1) - 1) - 1
    if kind == 'sine':  return np.sin(2 * np.pi * ph)
    raise ValueError(kind)

def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc; y[i] = acc
    return y

def lp_fast(x, cutoff, passes=2):
    # cheap one-pole via scipy if available
    try:
        from scipy.signal import lfilter
        a = np.exp(-2 * np.pi * cutoff / SR)
        for _ in range(passes):
            x = lfilter([1 - a], [1, -a], x)
        return x
    except ImportError:
        return lowpass(x, cutoff)

def hp_fast(x, cutoff):
    return x - lp_fast(x, cutoff, 1)

class Track:
    def __init__(self, bpm, bars, beats_per_bar=4):
        self.bpm = bpm; self.beat = 60.0 / bpm
        self.length = int(round(bars * beats_per_bar * self.beat * SR))
        self.L = np.zeros(self.length + SR * 3); self.R = np.zeros_like(self.L)
        self.duck = np.ones_like(self.L)

    def put(self, sig, start_beat, pan=0.0, gain=1.0, wrap=True):
        s = int(round(start_beat * self.beat * SR))
        gl, gr = gain * np.sqrt(0.5 * (1 - pan)), gain * np.sqrt(0.5 * (1 + pan))
        n = len(sig)
        end = s + n
        self.L[s:end] += sig * gl; self.R[s:end] += sig * gr

    def note(self, kind, name, start, dur, gain=0.3, pan=0.0, a=0.005, d=0.08, s=0.7, r=0.08, cutoff=None, detune=0.0, pw=0.5, vib=0.0):
        f = hz(name) if isinstance(name, str) else name
        n = int((dur * self.beat + r) * SR)
        x = osc(kind, f, n, 0, pw)
        if detune: x = 0.5 * (x + osc(kind, f, n, detune, pw))
        if vib:
            t = np.arange(n) / SR
            x = np.sin(2 * np.pi * np.cumsum(f * (1 + vib * np.sin(2 * np.pi * 5.5 * t) * np.clip(t * 3, 0, 1))) / SR) if kind == 'sine' else x
        if cutoff: x = lp_fast(x, cutoff)
        x *= env(n, a, d, s, r, dur * self.beat)
        self.put(x, start, pan, gain)

    def kick(self, start, gain=0.9):
        n = int(0.32 * SR); t = np.arange(n) / SR
        f = 48 + 110 * np.exp(-t * 32)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)
        x += 0.3 * np.exp(-t * 300) * rng.standard_normal(n) * 0.3
        self.put(x, start, 0, gain)
        s = int(round(start * self.beat * SR)); dl = int(0.22 * SR)
        self.duck[s:s + dl] = np.minimum(self.duck[s:s + dl], 0.45 + 0.55 * (np.arange(dl) / dl) ** 0.6)

    def snare(self, start, gain=0.45):
        n = int(0.25 * SR); t = np.arange(n) / SR
        noise = hp_fast(rng.standard_normal(n), 1200) * np.exp(-t * 18)
        tone = np.sin(2 * np.pi * 185 * t) * np.exp(-t * 30)
        self.put(0.7 * noise + 0.5 * tone, start, 0.05, gain)

    def clap(self, start, gain=0.35):
        n = int(0.2 * SR); t = np.arange(n) / SR
        e = np.zeros(n)
        for k in (0, 0.011, 0.022):
            i = int(k * SR); e[i:] += np.exp(-(t[: n - i]) * 40)
        x = hp_fast(rng.standard_normal(n), 900) * e
        self.put(x, start, -0.1, gain)

    def hat(self, start, gain=0.12, open_=False, pan=0.25):
        n = int((0.18 if open_ else 0.05) * SR); t = np.arange(n) / SR
        x = hp_fast(rng.standard_normal(n), 7000) * np.exp(-t * (14 if open_ else 70))
        self.put(x, start, pan, gain)

    def crash(self, start, gain=0.18):
        n = int(1.6 * SR); t = np.arange(n) / SR
        x = hp_fast(rng.standard_normal(n), 4000) * np.exp(-t * 2.2)
        self.put(x, start, 0, gain)

    def riser(self, start, beats, gain=0.12):
        n = int(beats * self.beat * SR); t = np.linspace(0, 1, n)
        x = hp_fast(rng.standard_normal(n), 2000) * t ** 2
        self.put(x, start, 0, gain)

    def render(self, loop=True, reverb=0.18, master=0.84):
        L, R = self.L.copy(), self.R.copy()
        # sidechain pump on everything except we approximate by ducking whole mix lightly
        # simple stereo room reverb (short, no audible echo)
        for buf, delays in ((L, (0.0297, 0.0371, 0.0411, 0.0437)), (R, (0.0311, 0.0353, 0.0423, 0.0451))):
            wet = np.zeros_like(buf)
            for dsec in delays:
                d = int(dsec * SR); g = 0.62
                y = buf.copy()
                for _ in range(6):
                    y = np.concatenate([np.zeros(d), y[:-d]]) * g
                    wet += y
            buf += lp_fast(wet, 5000, 1) * reverb * 0.25
        if loop:
            tail_L, tail_R = L[self.length:], R[self.length:]
            L = L[: self.length].copy(); R = R[: self.length].copy()
            k = min(len(tail_L), self.length)
            L[:k] += tail_L[:k]; R[:k] += tail_R[:k]
        else:
            nz = np.where(np.abs(L) + np.abs(R) > 1e-4)[0]
            end = nz[-1] + int(0.05 * SR) if len(nz) else len(L)
            L, R = L[:end], R[:end]
        st = np.stack([L, R], 1)
        st = np.tanh(st * 1.15) / np.tanh(1.15)
        peak = np.max(np.abs(st)); st = st / peak * master
        return st

def chord(root_oct, names):
    return names

# ---------------------------------------------------------------- themes
def theme_menu():
    # "Desk Lamp Lounge" – warm lofi-synth, 96 BPM, 16 bars, Fmaj7 – Em7 – Dm7 – Cmaj7 (x4 w/ variation)
    t = Track(96, 16)
    prog = [['F3', 'A3', 'C4', 'E4'], ['E3', 'G3', 'B3', 'D4'], ['D3', 'F3', 'A3', 'C4'], ['C3', 'E3', 'G3', 'B3']]
    bass = ['F2', 'E2', 'D2', 'C2']
    for bar in range(16):
        c = prog[bar % 4]; b0 = bar * 4
        for i, nm in enumerate(c):
            t.note('saw', nm, b0, 3.8, gain=0.055, pan=(-0.4 + 0.27 * i), a=0.25, d=0.6, s=0.6, r=0.6, cutoff=1400, detune=0.004)
        t.note('tri', bass[bar % 4], b0, 1.5, gain=0.33, cutoff=900, r=0.1)
        t.note('tri', bass[bar % 4], b0 + 2.5, 1.0, gain=0.28, cutoff=900, r=0.1)
        if bar >= 2:
            t.kick(b0); t.kick(b0 + 2.5, 0.6); t.snare(b0 + 1, 0.3); t.snare(b0 + 3, 0.3)
            for h in range(8): t.hat(b0 + h * 0.5 + (0.06 if h % 2 else 0), 0.06 if h % 2 else 0.09)
        # sparkly arpeggio (bell)
        for k in range(8):
            nm = c[[0, 2, 1, 3, 2, 1, 3, 2][k]]
            octv = nm[:-1] + str(int(nm[-1]) + 1)
            t.note('sine', octv, b0 + k * 0.5, 0.45, gain=0.07, pan=0.35 * (1 if k % 2 else -1), a=0.002, d=0.25, s=0.0, r=0.2)
    mel = [('A4', 0, 1.5), ('C5', 1.5, 0.5), ('B4', 2, 1), ('G4', 3, 1), ('A4', 4, 2), ('E4', 6, 2),
           ('F4', 8, 1), ('A4', 9, 1), ('C5', 10, 1.5), ('D5', 11.5, 0.5), ('B4', 12, 2), ('G4', 14, 2)]
    for rep in (16, 48):
        for nm, st, du in mel:
            t.note('sq', nm, rep + st, du, gain=0.07, pw=0.3, a=0.01, d=0.2, s=0.55, r=0.15, cutoff=2600, pan=0.1)
    return t.render(loop=True, reverb=0.35)

def race_theme(bpm, key_shift, lead_kind, seed, energy):
    # 16-bar driving loop. Progression i-VI-III-VII in minor (Am F C G) shifted.
    t = Track(bpm, 16)
    def sh(nm, k=key_shift):
        n, o = nm[:-1], int(nm[-1]); v = NOTE[n] + k
        return list(NOTE)[v % 12] + str(o + v // 12)
    prog = [['A3', 'C4', 'E4'], ['F3', 'A3', 'C4'], ['C4', 'E4', 'G4'], ['G3', 'B3', 'D4']]
    bass = ['A1', 'F1', 'C2', 'G1']
    r = np.random.default_rng(seed)
    for bar in range(16):
        b0 = bar * 4; c = [sh(x) for x in prog[bar % 4]]; bn = sh(bass[bar % 4])
        section = bar // 4  # 0 intro-ish, 1 A, 2 B (lift), 3 A'
        # drums
        for q in range(4):
            t.kick(b0 + q, 0.85)
        if section > 0 or bar % 4 == 3:
            t.snare(b0 + 1, 0.42); t.snare(b0 + 3, 0.42); t.clap(b0 + 3, 0.25)
        for h in range(8):
            t.hat(b0 + h * 0.5 + 0.5 * (h % 2 == 0) * 0, 0.11 if h % 2 else 0.05, open_=(h % 2 == 1 and section == 2))
        if energy > 0.6 and section >= 1:
            for h in range(16): t.hat(b0 + h * 0.25, 0.03, pan=-0.3)
        if bar % 4 == 0: t.crash(b0, 0.15 if bar else 0.2)
        if bar == 15: t.riser(b0, 4, 0.1)
        # octave-bounce bass (8ths)
        for e in range(8):
            nm = bn if e % 2 == 0 else bn[:-1] + str(int(bn[-1]) + 1)
            t.note('saw', nm, b0 + e * 0.5, 0.42, gain=0.22, a=0.002, d=0.1, s=0.5, r=0.03, cutoff=700 + 500 * energy)
        # pad stabs on off-beats (section 1,3) or sustained (section 2)
        if section == 2:
            for i, nm in enumerate(c):
                t.note('saw', nm, b0, 3.9, gain=0.05, pan=-0.5 + 0.5 * i, a=0.05, d=0.3, s=0.8, r=0.2, cutoff=2500, detune=0.006)
        else:
            for off in (0.5, 1.5, 2.5, 3.5):
                for i, nm in enumerate(c):
                    t.note('sq', nm, b0 + off, 0.25, gain=0.035, pan=-0.4 + 0.4 * i, a=0.002, d=0.1, s=0.3, r=0.05, cutoff=3000, pw=0.25)
        # 16th arpeggio
        if section >= 1:
            pat = [0, 1, 2, 1, 0, 2, 1, 2, 0, 1, 2, 1, 2, 1, 0, 1]
            for k in range(16):
                nm = c[pat[k]]; nm = nm[:-1] + str(int(nm[-1]) + 1)
                t.note('sq', nm, b0 + k * 0.25, 0.2, gain=0.03, pan=0.45 * (1 if k % 2 else -1), a=0.001, d=0.06, s=0.2, r=0.03, pw=0.125)
    # lead melody: motif-based, generated from chord tones & passing tones, repeated with variation
    scale = [sh(x) for x in ['A4', 'B4', 'C5', 'D5', 'E5', 'F5', 'G5', 'A5']]
    rhythm = [(0, 0.75), (0.75, 0.75), (1.5, 0.5), (2, 1), (3, 0.5), (3.5, 0.5)]
    motifs = []
    for m in range(4):
        degs = [int(r.integers(0, 6)) for _ in rhythm]
        degs[0] = [0, 5, 2, 4][m]  # land on chord root-ish
        motifs.append(degs)
    for bar in range(4, 16):
        if bar // 4 == 2 and bar % 2 == 1: continue  # breathing room in B section
        b0 = bar * 4; m = motifs[bar % 4]
        lift = 1 if bar // 4 == 2 else 0
        for (st, du), dg in zip(rhythm, m):
            nm = scale[min(dg + lift, 7)]
            t.note(lead_kind, nm, b0 + st, du * 0.9, gain=0.09, pw=0.4, a=0.004, d=0.12, s=0.6, r=0.06, cutoff=4200, pan=0.05, detune=0.003 if lead_kind == 'saw' else 0)
            t.note(lead_kind, nm, b0 + st + 0.75, du * 0.5, gain=0.025, pw=0.4, a=0.004, d=0.1, s=0.4, r=0.05, cutoff=2500, pan=-0.4)  # call-back echo note (dotted 8th, quiet)
    return t.render(loop=True, reverb=0.15)

def jingle_countdown():
    t = Track(120, 2)
    for i, nm in enumerate(['A4', 'A4', 'A4']):
        t.note('sq', nm, i, 0.35, gain=0.25, pw=0.5, a=0.002, d=0.2, s=0.3, r=0.05)
    for nm in ('A5', 'E5', 'C#5'):
        t.note('sq', nm, 3, 0.9, gain=0.18, pw=0.25, a=0.002, d=0.4, s=0.5, r=0.2)
    t.crash(3, 0.2)
    return t.render(loop=False, reverb=0.1)

def jingle_finish_win():
    t = Track(150, 4)
    seq = [('C5', 0, 0.5), ('E5', 0.5, 0.5), ('G5', 1, 0.5), ('C6', 1.5, 1.0), ('A5', 2.5, 0.5), ('C6', 3, 0.5), ('D6', 3.5, 0.5), ('E6', 4, 2.5)]
    for nm, st, du in seq:
        t.note('sq', nm, st, du, gain=0.16, pw=0.3, a=0.003, d=0.15, s=0.7, r=0.12)
        t.note('saw', nm, st, du, gain=0.05, a=0.003, d=0.15, s=0.7, r=0.12, cutoff=3000)
    for nm in ('C3', 'G3', 'C4', 'E4', 'G4'):
        t.note('saw', nm, 4, 2.5, gain=0.05, a=0.02, d=0.4, s=0.7, r=0.4, cutoff=2400, detune=0.006)
    for b in (0, 1, 2, 3, 4): t.kick(b, 0.7)
    t.snare(1, 0.3); t.snare(3, 0.3); t.crash(4, 0.25)
    return t.render(loop=False, reverb=0.3)

def jingle_finish_lose():
    t = Track(110, 3)
    for nm, st, du in [('E5', 0, 0.5), ('D5', 0.5, 0.5), ('C5', 1, 0.5), ('B4', 1.5, 0.5), ('A4', 2, 2)]:
        t.note('sq', nm, st, du, gain=0.15, pw=0.4, a=0.003, d=0.2, s=0.6, r=0.15)
    for nm in ('A2', 'E3', 'A3', 'C4'):
        t.note('saw', nm, 2, 2, gain=0.05, a=0.02, d=0.4, s=0.6, r=0.4, cutoff=1800)
    return t.render(loop=False, reverb=0.3)

def jingle_final_lap():
    t = Track(160, 2)
    for i, nm in enumerate(['E5', 'G5', 'B5', 'E6']):
        t.note('sq', nm, i * 0.5, 0.45, gain=0.16, pw=0.25, a=0.002, d=0.1, s=0.6, r=0.05)
    t.note('saw', 'E6', 2, 1.5, gain=0.1, a=0.002, d=0.2, s=0.7, r=0.2, cutoff=4000, detune=0.005)
    t.crash(2, 0.2)
    return t.render(loop=False, reverb=0.2)

if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    items = {
        'menu_loop': theme_menu(),
        'race_study_desk_loop': race_theme(138, 0, 'sq', 11, 0.7),
        'race_gamer_desk_loop': race_theme(128, -2, 'saw', 23, 0.9),
        'race_kitchen_loop': race_theme(146, 3, 'sq', 37, 0.8),
        'race_bonus_loop': race_theme(132, 5, 'saw', 51, 0.85),
        'jingle_countdown': jingle_countdown(),
        'jingle_final_lap': jingle_final_lap(),
        'jingle_win': jingle_finish_win(),
        'jingle_finish': jingle_finish_lose(),
    }
    for k, v in items.items():
        p = os.path.join(out, k + '.ogg')
        sf.write(p, v.astype(np.float32), SR, format='OGG', subtype='VORBIS')
        print(f'{p}: {len(v)/SR:.2f}s')
