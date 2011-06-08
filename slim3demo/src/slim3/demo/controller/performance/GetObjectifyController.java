package slim3.demo.controller.performance;

import org.slim3.controller.Controller;
import org.slim3.controller.Navigation;

import slim3.demo.cool.model.BarObjectify;
import slim3.demo.cool.service.PerformanceService;

public class GetObjectifyController extends Controller {

    private PerformanceService service = new PerformanceService();

    @Override
    public Navigation run() throws Exception {
        long start = System.currentTimeMillis();
        for (BarObjectify bar : service.getBarListUsingObjectify()) {
            bar.getKey();
            bar.getSortValue();
        }
        sessionScope("getObjectify", System.currentTimeMillis() - start);
        return redirect(basePath);
    }
}
