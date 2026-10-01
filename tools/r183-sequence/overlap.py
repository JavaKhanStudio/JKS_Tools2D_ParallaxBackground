#!/usr/bin/env python3
"""Which q04 overlap layer a search over its slots would draw wrongly (r183): its slots seen at t0, t6 and t12 (x 0, -18,
-36, no repeat, the layer scrolling 3 units/s) walked one by one against ParallaxLayer.drawCycle's binary search and
early stop. A line per layer where the search misses slots at two moments or more; make_round.py takes one."""
M=0xFFFFFFFF
def nxt(s):
    s^=(s<<13)&M; s^=s>>17; s^=(s<<5)&M; return s
def draw(seed,w,n):
    t=sum(x for x in w if x>0); s=seed&M or 0x6D2B79F5; out=[]
    for _ in range(n):
        s=nxt(s); p=(s>>1)%(t if t>0 else len(w)); k=0
        while k<len(w)-1:
            ww=max(w[k],0) if t>0 else 1
            if p<ww: break
            p-=ww; k+=1
        out.append(k)
    return out
A={'stone':2,'bridge':3.5,'river':1.25,'tower':0.75,'bush':1.5}
def vis(segs,ws,seed,n,size,pad,x,ordered):
    H=40*size/A[segs[0]]; c=draw(seed,ws,n); e=[0]
    for k in c: e.append(e[-1]+A[segs[k]])
    first=0
    if ordered:
        last=n
        while first<last:
            m=(first+last)//2
            if x+H*e[m+1]+m*pad>0: last=m
            else: first=m+1
    out=[]
    for s in range(first,n):
        l=x+H*e[s]+s*pad
        if l>=40:
            if ordered: break
            continue
        if l+H*A[segs[c[s]]]<=0: continue
        out.append(s)
    return out
import itertools
for segs,ws in [(['bridge','tower','stone'],[1,4,1]),(['stone','tower','bridge'],[1,3,2]),(['stone','tower','river','bridge'],[1,4,1,1]),(['stone','tower','river'],[1,3,1]),(['bridge','tower','stone'],[1,3,1])]:
  for pad in (-6,-8,-10,-12,-14,-16,-20):
    for seed in range(0,40):
      d=[]
      for x in (0,-18,-36):
        a=vis(segs,ws,seed,10,0.35,pad,x,True); b=vis(segs,ws,seed,10,0.35,pad,x,False)
        d.append(len(set(b)-set(a)))
      H=40*0.35/A[segs[0]]; c=draw(seed,ws,10)
      width=H*sum(A[segs[k]] for k in c)+9*pad
      # A cycle no wider than 0 can't tile and is drawn once (r202): only a positive one shows the search when tiled.
      if width>0 and sum(1 for v in d if v)>=2: print(segs,ws,pad,seed,d,'cycle %.1f wide'%width)
