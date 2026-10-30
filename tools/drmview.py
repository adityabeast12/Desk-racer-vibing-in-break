import sys, struct, numpy as np, matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
def load(p):
    b=open(p,'rb').read(); assert b[:4]==b'DRM1'; o=4; (n,)=struct.unpack_from('<i',b,o); o+=4; parts=[]
    for _ in range(n):
        (l,)=struct.unpack_from('<i',b,o); o+=4; name=b[o:o+l].decode(); o+=l
        piv=np.array(struct.unpack_from('<3f',b,o)); o+=12
        (vc,)=struct.unpack_from('<i',b,o); o+=4
        d=np.frombuffer(b,'<f4',vc*10,o).reshape(vc,10); o+=vc*40
        (ic,)=struct.unpack_from('<i',b,o); o+=4
        idx=np.frombuffer(b,'<u2',ic,o).reshape(-1,3); o+=ic*2
        o+=(-o)%4; parts.append((name,piv,d,idx))
    return parts
fig=plt.figure(figsize=(12,4))
for k,(p,paint) in enumerate([(sys.argv[1],None),(sys.argv[1],(0.1,0.5,1.0)),(sys.argv[2],None)]):
    ax=fig.add_subplot(1,3,k+1,projection='3d'); polys=[];cols=[]
    for name,piv,d,idx in load(p):
        pos=d[:,:3]+piv; c=d[:,6:9].copy()
        if paint: c[d[:,9]>0.5]=paint
        for t in idx:
            polys.append(pos[t][:,[0,2,1]]); cols.append(c[t].mean(0))
    ax.add_collection3d(Poly3DCollection(polys,facecolors=cols,edgecolor='none'))
    a=np.concatenate(polys); ax.auto_scale_xyz(a[:,0],a[:,1],a[:,2]); ax.view_init(25,-60); ax.set_title(p.split('/')[-1]+(' repainted' if paint else ''))
plt.savefig(sys.argv[3],dpi=80)
