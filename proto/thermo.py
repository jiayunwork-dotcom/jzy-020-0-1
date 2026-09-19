# Prototype: Shomate-style Cp polynomials, formation enthalpies, flame temp solve.
# Cp/R? We work in J/mol/K directly (Shomate: Cp = A + B*t + C*t^2 + D*t^3 + E/t^2, t=T/1000)
# H sensible from 298.15: integrate Cp dT (split at segment breaks).
# h_total(T) = dHf(298.15) + sensible.

T0 = 298.15

# NIST Shomate coefficients: list of (Tlo, Thi, A,B,C,D,E)
SPECIES = {}

SPECIES["O2"] = [  # NIST O2 100-700 / 700-2000; extend high segment evaluated up to 3500
 (100,700, 31.32234,-20.23531,57.86644,-36.50624,-0.007374),
 (700,2000, 30.03235,8.772972,-3.988133,0.788313,-0.741599),
]
SPECIES["N2"] = [
 (100,500, 28.98641,1.853978,-9.647459,16.63537,0.000117),
 (500,2000, 19.50583,19.88705,-8.598535,1.369784,0.527601),
 (2000,6000, 35.51872,1.128728,-0.196103,0.014662,-4.553760),
]
SPECIES["H2O"] = [  # gas
 (500,1700, 30.09200,6.832514,6.793435,-2.534480,0.082139),
 (1700,6000, 41.96426,8.622053,-1.499780,0.098119,-11.15764),
]
SPECIES["CO2"] = [
 (298,1200, 24.99735,55.18696,-33.69137,7.948387,-0.136638),
 (1200,6000, 58.16639,2.720074,-0.492289,0.038844,-6.447293),
]
SPECIES["CH4"] = [
 (298,1300, -0.703029,108.4773,-42.52157,5.862788,0.678565),
 (1300,6000, 85.81217,11.26467,-2.114146,0.138190,-26.42221),
]
# Ethane: high segment from recalled NIST (1200-6000). Low segment fitted below.
SPECIES["C2H6_HIGH"] = [
 (1200,6000, 126.9238,4.298441,-0.137771,0.004966,-21.30209),
]

# dHf kJ/mol (298.15)
DHF = {"CH4":-74.6, "C2H6":-84.0, "O2":0.0, "N2":0.0, "CO2":-393.51, "H2O":-241.826}

DOMAIN = (200.0, 3500.0)

def cp_of(coefs, T):
    t=T/1000.0
    A,B,C,D,E=coefs
    return A + B*t + C*t**2 + D*t**3 + E/t**2

def sensible(coefset, T, Tref=T0):
    # integrate Cp from Tref to T, splitting at breaks; Cp continuous within coefset spans.
    if T==Tref: return 0.0
    lo,hi=sorted((Tref,T))
    # collect breakpoints
    pts=[lo]
    for (a,b,*_ ) in coefset:
        for x in (a,b):
            if lo < x < hi: pts.append(x)
    pts.append(hi)
    pts=sorted(set(pts))
    total=0.0
    for x1,x2 in zip(pts,pts[1:]):
        Tm=(x1+x2)/2
        seg=[s for s in coefset if s[0]<=Tm<=s[1]]
        if not seg:
            # allow at boundaries
            seg=[s for s in coefset if s[0]-1e-9<=Tm<=s[1]+1e-9]
        A,B,C,D,E=seg[0][2:]
        def antider(T):
            t=T/1000.0
            # integral of Cp dT (J/mol): H = A*T + B*t^2/2*1000 ... use Shomate formula:
            # H-H(298) kJ/mol = A*t + B*t^2/2 + C*t^3/3 + D*t^4/4 - E/t + F
            return 1000.0*(A*t + B*t**2/2 + C*t**3/3 + D*t**4/4 - E/t)
        total += antider(x2)-antider(x1)
    if T<Tref: total=-total
    return total

def total_h(species,T):
    return DHF[species]*1000.0 + sensible(SPECIES[species],T)

if __name__=="__main__":
    # sanity Cp
    for sp,T in [("CO2",298.15),("CO2",1000),("H2O",500),("H2O",1700),("N2",298.15),
                 ("N2",2000),("O2",298.15),("O2",2000),("CH4",298.15),("CH4",1300)]:
        seg=[s for s in SPECIES[sp] if s[0]<=T<=s[1]][0][2:]
        print(sp,T,"Cp=",round(cp_of(seg,T),2))
