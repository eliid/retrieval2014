<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="/WEB-INF/include/taglib.jsp"%>
<html>
<head>
	<title>图片搜索</title>
	<meta name="decorator" content="default"/>
	<link href="${ctxStatic}/jquery-blocksit/style.css" type="text/css" rel="stylesheet" media='screen'/>
	<script src="${ctxStatic}/jquery-blocksit/jquery.min.js" type="text/javascript"></script>
	<script src="${ctxStatic}/jquery-blocksit/html5.js" type="text/javascript"></script>
	<script src="${ctxStatic}/jquery-blocksit/blocksit.min.js" type="text/javascript"></script>
	<style type="text/css">
		/* body{
			overflow: hidden;
		} */
		a:link {
		color:'#0000d5';
		text-decoration:underline;
		front-style:normal;
		font-family:arial;
		font-size:normal;
		font-weight:normal;
		}
		.input-medium{
			font:16px/22px arial;
			width:450px;
			height:30px;
			margin-left:15px;
		}
		.info{
			width:100%;
			height:30px;
			line-height:30px;
			background-color:#D6E7EE;
            font-family: verdana;
			letter-spacing:2px;
			font-size:13px;
			padding-left:15px;
		}
		font{
		size:3;
		}
		.cont{
			margin-top:3px;
			margin-bottom:3px;
			font-size:13px;
			font-family:arial,sans-serif;
		}
         .searchBtn{
         	width:65px;
         	height:30px;
         	margin-left:15px;
         	background-color:
         }
          .main{
         	height:90%;
         	/* overflow-y: auto; */
         }
         .container{
         	margin-left:0px;
         }
	</style>
	<script type="text/javascript">
	$(document).ready(function() {
		/* window.onload = function() { 
			var totalCount= $('#totalCount').val();
			var pageSize = Math.ceil(totalCount/$('#pageSize').val());
			var pageNo = $('#pageNo').val();
			
			$("#pages").paginate({
				count 		: pageSize,
				start 		: pageNo,
				display     : 10,
				border					: true,
				border_color			: '#C2D5E3',
				text_color  			: '#1E63A0',
				background_color    	: 'white',	
				border_hover_color		: '#1E63A0',
				text_hover_color  		: 'red',
				background_hover_color	: '#CFE2ED', 
				//rotate      : false,
				images		: true,
				mouse		: 'press',
				onChange : function(page){
					$("#pageNo").val(page);
					$("#searchForm").submit();
				}
			});
		}; */
		
		$(window).on("scroll",function(){
			//alert($(window).scrollTop()+$(window).height());
			if($(document).height() <= $(window).scrollTop()+$(window).height()+1){
			//当最短的ul的高度比窗口滚出去的高度+浏览器高度大时加载新图片
				var kw = $("#keyword").val();
				var pz = $("#pageSize").val();
				var pn = Number($("#pageNo").val())+1;
				$.ajax({
				    url: '/f/search/jsonImg/'+kw+'?pageSize='+pz+'&pageNum='+pn,
				    success: function (data) {
				    	var retrievalPageList = data.retrievalPageList;
				    	for(var i=0;i<retrievalPageList.length;i++){
				    		$("#container").append('<div class="grid">'+
									'<div class="imgholder">'+
										'<img src="${ctxStatic}/'+retrievalPageList[i].retrievalResultFields._PATH+'" />'+
									'</div>'+
									'<strong>'+retrievalPageList[i].title+'</strong>'+
									'<p>'+retrievalPageList[i].content+'</p>'+
									'<div class="meta">'+retrievalPageList[i].retrievalResultFields.CREATETIME+'</div>'+
								'</div>').BlocksIt('reload'); 
				    	}
				    	$("#pageSize").val(pz);
				    	$("#pageNo").val(pn);
				    },
				    cache: false
				});
			}
		});

		//blocksit define
		$(window).load( function() {
			$('#container').BlocksIt({
				numOfCol: 5,
				offsetX: 8,
				offsetY: 8
			});
		});
		
		//window resize
		var currentWidth = 1100;
		$(window).resize(function() {
			var winWidth = $(window).width();
			var conWidth;
			if(winWidth < 660) {
				conWidth = 440;
				col = 2
			} else if(winWidth < 880) {
				conWidth = 660;
				col = 3
			} else if(winWidth < 1100) {
				conWidth = 880;
				col = 4;
			} else {
				conWidth = 1100;
				col = 5;
			}
			
			if(conWidth != currentWidth) {
				currentWidth = conWidth;
				$('#container').width(conWidth);
				$('#container').BlocksIt({
					numOfCol: col,
					offsetX: 8,
					offsetY: 8
				});
			}
		});
		
	});
	</script>
</head>
<body>
	<div id="upper">
		<div style="float:right">
			<a href="${ctx_f}/search/index">返回首页</a>
		</div>
		<div>
			<form id="searchForm" action="${ctx_f}/search/image" method="post" class="breadcrumb form-search">
				<input id="pageNo" name="pageNo" type="hidden" value="${page.pageNo}"/>
				<input id="pageSize" name="pageSize" type="hidden" value="${page.pageSize}"/>
				<input id="totalCount" name="totalCount" type="hidden" value="${page.count}"/>
				<div style="margin-top:5px;font-size:25px;font-weight:normal;font-familiy:Helvetica, Georgia, Arial, sans-serif, 黑体;float:left">${fns:getConfig('productName')}</div>
				<input id="keyword" name="simpleQuery.keyword" type="text" maxlength="200" class="input-medium" value="${simpleQuery.keyword}"/>
				<input id="btnSubmit" class="searchBtn" type="submit" value="搜索" /><br>
			</form>
		</div>
		<div class="info">
			<span>找到约 <font color="red">${page.count}</font>条结果，用时<font color="red">${time}</font>秒
				 	<c:forEach items="${page.group}" var="group">
				 		<a href="#" onclick="javascript:alert('${group.key}');return false;">${group.key}</a>(${group.value})
				 	</c:forEach>
			</span>
		</div>
	</div>
	<div class="main">
		<div style="float:left">
			<div id="container">
				<c:forEach items="${page.list}" var="retrievalPage">
					<div class="grid">
						<div class="imgholder">
							<img src="${ctxStatic}/${retrievalPage.retrievalResultFields['_PATH']}" />
						</div>
						<strong>${retrievalPage.title}</strong>
						<p>${retrievalPage.content}</p>
						<div class="meta">${retrievalPage.retrievalResultFields['CREATETIME']}</div>
					</div>
				</c:forEach>
				<!---->
			</div>
		</div>
	</div>
</body>
</html>