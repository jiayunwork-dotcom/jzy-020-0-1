import math
R=8.314462618
# NASA7 from GRI-Mech 3.0 (Cantera data/gri30.yaml): ranges then [a1..a7] low, high
NASA={
"CH4":((200,1000,3500),
 [5.14987613,-0.0136709788,4.91800599e-05,-4.84743026e-08,1.66693956e-11,-1.02466476e+04,-4.64130376],
 [0.074851495,0.0133909467,-5.73285809e-06,1.22292535e-09,-1.01815235e-13,-9468.34459,18.437318]),
"C2H6":((200,1000,3500),
 [4.29142492,-5.5015427e-03,5.99438288e-05,-7.08466285e-08,2.68685771e-11,-1.15222055e+04,2.66682316],
 [1.0718815,0.0216852677,-1.00256067e-05,2.21412001e-09,-1.9000289e-13,-1.14263932e+04,15.1156107]),
"O2":((200,1000,3500),
 [3.78245636,-2.99673416e-03,9.84730201e-06,-9.68129509e-09,3.24372837e-12,-1063.94356,3.65767573],
 [3.28253784,1.48308754e-03,-7.57966669e-07,2.09470555e-10,-2.16717794e-14,-1088.45772,5.45323129]),
"N2":((300,1000,5000),
 [3.298677,1.4082404e-03,-3.963222e-06,5.641515e-09,-2.444854e-12,-1020.8999,3.950372],
 [2.92664,1.4879768e-03,-5.68476e-07,1.0097038e-10,-6.753351e-15,-922.7977,5.980528]),
"CO2":((200,1000,3500),
 [2.35677352,8.98459677e-03,-7.12356269e-06,2.45919022e-09,-1.43699548e-13,-4.83719697e+04,9.90105222],
 [3.85746029,4.41437026e-03,-2.21481404e-06,5.23490188e-10,-4.72084164e-14,-4.8759166e+04,2.27163806]),
"H2O":((200,1000,3500),
 [4.19864056,-2.0364341e-03,6.52040211e-06,-5.48797062e-09,1.77197817e-12,-3.02937267e+04,-0.849032208],
 [3.03399249,2.17691804e-03,-1.64072518e-07,-9.7041987e-11,1.68200992e-14,-3.00042971e+04,4.9667701]),
}
TLO,THI=200.0,3500.0
def coeff(sp,T):
    if not (TLO<=T<=THI): raise ValueError("out of domain")
    (t1,t2,t3),lo,hi=NASA[sp]
    return lo if T<t2 else hi
def h(sp,T):
    a=coeff(sp,T)
    return R*(a[0]*T+a[1]*T**2/2+a[2]*T**3/3+a[3]*T**4/4+a[4]*T**5/5+a[5])  # J/mol
def cp(sp,T):
    a=coeff(sp,T)
    return R*(a[0]+a[1]*T+a[2]*T**2+a[3]*T**3+a[4]*T**4)

FUELS={"CH4":(1,4),"C2H6":(2,6)}
def composition(fuel,phi):
    n,m=FUELS[fuel]; os_=n+m/4
    oin=os_/phi; nin=oin*0.79/0.21
    r=min(1.0,1.0/phi)
    prod={"CO2":n*r,"H2O":(m/2)*r,"O2":oin-os_*r,"N2":nin}
    if r<1.0: prod[fuel]=(1.0-r)
    react={fuel:1.0,"O2":oin,"N2":nin}
    return react,prod
def atoms(mixture):
    C=H=O=N=0.0
    for sp,x in mixture.items():
        c={"CH4":(1,4,0,0),"C2H6":(2,6,0,0),"O2":(0,0,2,0),"N2":(0,0,0,2),"CO2":(1,0,2,0),"H2O":(0,2,1,0)}[sp]
        C+=c[0]*x;H+=c[1]*x;O+=c[2]*x;N+=c[3]*x
    return C,H,O,N

TOL=10.0
def solve(fuel,phi,Tin,maxacc=50,maxprobe=400,verbose=False):
    react,prod=composition(fuel,phi)
    Hr=sum(x*h(sp,Tin) for sp,x in react.items())
    def F(T): return sum(x*h(sp,T) for sp,x in prod.items())-Hr
    lo,hi=TLO,THI
    flo,fhi=F(lo),F(hi)
    if flo>0 or fhi<0: raise RuntimeError("root not bracketed")
    log=[]
    bestT=bestF=None
    def accept(T,f):
        nonlocal bestT,bestF
        if bestF is None or abs(f)<abs(bestF):
            bestT,bestF=T,f; log.append((T,f)); return True
        return False
    probes=0
    while len(log)<maxacc and probes<maxprobe:
        # secant prediction from bracket values, clipped; else midpoint
        c=(lo+hi)/2
        f=F(c); probes+=1
        if accept(c,f) or True:
            pass
        if not accept(c,f):
            pass
        # accept logic handled below explicitly
        if abs(f)<=TOL:
            if not log or log[-1]!=(c,f): log.append((c,f))
            return bestT if bestF is not None and abs(bestF)<=abs(f) else c, log, probes
        if f<0: lo=c; flo=f
        else: hi=c; fhi=f
        # candidate accepted point: we log only improvements; probe mid repeatedly (shrink)
        # ensure monotonic log: append c only if improves
        # (already attempted above); loop continues
    raise RuntimeError("no converge")

# Cleaner: separate probes vs accepted
def solve2(fuel,phi,Tin,maxacc=50,maxprobe=400):
    react,prod=composition(fuel,phi)
    Hr=sum(x*h(sp,Tin) for sp,x in react.items())
    def F(T): return sum(x*h(sp,T) for sp,x in prod.items())-Hr
    lo,hi=TLO,THI
    flo,fhi=F(lo),F(hi)
    if flo>0 or fhi<0: raise RuntimeError("root not bracketed: %g %g"%(flo,fhi))
    log=[]  # accepted (T,F), strictly decreasing |F|
    acc=0; probes=0
    best=None
    while acc<maxacc and probes<maxprobe:
        c=(lo+hi)/2
        f=F(c); probes+=1
        improved = best is None or abs(f)<abs(best[1])
        if f<0: lo, flo=c,f
        else: hi,fhi=c,f
        if improved:
            log.append((c,f)); best=(c,f); acc+=1
            if abs(f)<=TOL: return c,log,probes
        # if not improved: just keep shrinking bracket, not logged
    raise RuntimeError("no converge in %d probes"%probes)

def report(fuel,phi,Tin):
    react,prod=composition(fuel,phi)
    T,log,probes=solve2(fuel,phi,Tin)
    aR=atoms(react); aP=atoms(prod)
    res=max(abs(a-b) for a,b in zip(aR,aP))
    mono=all(abs(log[i][1])>abs(log[i+1][1]) for i in range(len(log)-1)) or \
         all(abs(log[i][1])>=abs(log[i+1][1]) for i in range(len(log)-1))
    nTot=sum(prod.values())
    print(f"{fuel} phi={phi} Tin={Tin}: T={T:.2f} K  iters={len(log)} probes={probes} "
          f"lastRes={log[-1][1]:.4f} J mono={mono} atomRes={res:.2e}")
    print("   mole fractions:", {k:round(v/nTot,5) for k,v in prod.items()})
    return T

t1=report("CH4",1.0,298.15)
tl=report("CH4",0.8,298.15)
tr=report("CH4",1.2,298.15)
te=report("C2H6",1.0,298.15)
th=report("CH4",1.0,400.0)
print("checks: stoich>lean",t1>tl," stoich>rich",t1>tr," ethane!=methane",abs(te-t1)>50)
print("inlet bump: dT_flame=%.2f < dT_in=101.85, flame rises"%(th-t1), th>t1 and (th-t1)<101.85)
# dHf sanity at 298.15
for sp in ["CH4","C2H6","CO2","H2O","O2","N2"]:
    print(sp,"h(298.15)=%.1f kJ/mol"%(h(sp,298.15)/1000))
