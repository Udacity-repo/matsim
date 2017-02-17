package playground.clruch.dispatcher.utils;

import java.util.Set;

import org.matsim.api.core.v01.network.Link;

import playground.clruch.netdata.VirtualNode;

public abstract class AbstractVirtualNodeDest {
    public abstract Set<Link> selectLinkSet(VirtualNode virtualNode, int size);
    
    // maybe insert option to treat case of local=within node behaviour
}
