package slim3.it.controller.blog;

import org.slim3.controller.JDOController;
import org.slim3.controller.Navigation;
import org.slim3.controller.validator.Validators;
import org.slim3.util.BeanUtil;

import slim3.it.model.Blog;

public class UpdateController extends JDOController {

    @Override
    public Navigation run() {
        if (!validate()) {
            return forward("edit.jsp");
        }
        Blog blog = pm.getObjectById(Blog.class, requestScope("key"));
        BeanUtil.copy(request, blog);
        return redirect(basePath);
    }

    protected boolean validate() {
        Validators v = new Validators(request);
        v.add("title", v.required());
        v.add("content", v.required());
        return v.validate();
    }
}