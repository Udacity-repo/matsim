package playground.clruch.dispatcher;

import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;

import playground.clruch.router.FuturePathContainer;

/**
 * class maintains a {@link FuturePathContainer}
 * while the path is being computer.
 * the resulting path is available upon the function call execute(...)
 */
abstract class FuturePathDirective extends AbstractDirective {
    protected final FuturePathContainer futurePathContainer;

    FuturePathDirective(FuturePathContainer futurePathContainer) {
        this.futurePathContainer = futurePathContainer;
    }
    
    @Override
    final void execute() {
        VrpPathWithTravelData vrpPathWithTravelData = futurePathContainer.getVrpPathWithTravelData();
        executeWithPath(vrpPathWithTravelData);
    }
    
    abstract void executeWithPath(VrpPathWithTravelData vrpPathWithTravelData);

}
