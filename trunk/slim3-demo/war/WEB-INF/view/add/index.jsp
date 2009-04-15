<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="html" uri="http://struts.apache.org/tags-html"%>  
<%@taglib prefix="bean" uri="http://struts.apache.org/tags-bean"%>  
<%@taglib prefix="tiles" uri="http://jakarta.apache.org/struts/tags-tiles"%>
<%@taglib prefix="s" uri="http://www.slim3.org/tags"%>
<%@taglib prefix="f" uri="http://www.slim3.org/functions"%>


<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>Add</title>
<link rel="stylesheet" type="text/css" href="../css/global.css" />
</head>
<body>
<h1>Add</h1>
<html:errors/>
<s:form>
<html:text property="arg1"/> +
<html:text property="arg2"/>
= ${f:h(result)}<br />
<input type="submit" name="submit" value="submit"/>
</s:form>
</body>
</html>