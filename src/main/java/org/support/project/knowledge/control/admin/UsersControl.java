package org.support.project.knowledge.control.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.support.project.common.bean.ValidateError;
import org.support.project.common.util.PropertyUtil;
import org.support.project.common.util.StringUtils;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.control.Control;
import org.support.project.knowledge.logic.MailLogic;
import org.support.project.knowledge.logic.UserLogic;
import org.support.project.knowledge.vo.Roles;
import org.support.project.web.annotation.Auth;
import org.support.project.web.boundary.Boundary;
import org.support.project.web.common.HttpStatus;
import org.support.project.web.common.HttpUtil;
import org.support.project.web.config.CommonWebParameter;
import org.support.project.web.dao.ProvisionalRegistrationsDao;
import org.support.project.web.dao.RolesDao;
import org.support.project.web.dao.UsersDao;
import org.support.project.web.entity.ProvisionalRegistrationsEntity;
import org.support.project.web.entity.RolesEntity;
import org.support.project.web.entity.UsersEntity;
import org.support.project.web.exception.InvalidParamException;

@DI(instance=Instance.Prototype)
public class UsersControl extends Control {

	public static final int PAGE_LIMIT = 100;
	
	/**
	 * ユーザー一覧を表示
	 * @return
	 * @throws InvalidParamException
	 */
	@Auth(roles="admin")
	public Boundary list() throws InvalidParamException {
		Integer offset = super.getPathInteger(0);
		if (offset == null) {
			offset = 0;
		}
		String keyword = super.getParam("keyword");
		return list(offset, keyword);
	}
	
	/**
	 * 一覧表示
	 * @param offset
	 * @param keyword
	 * @return
	 */
	@Auth(roles="admin")
	private Boundary list(Integer offset, String keyword) {
		UsersDao dao = UsersDao.get();
		List<UsersEntity> users = dao.selectOnKeyword(offset * PAGE_LIMIT, PAGE_LIMIT, keyword);
		for (UsersEntity usersEntity : users) {
			usersEntity.setPassword("");
		}
		setAttribute("users", users);
		
		int previous = offset -1;
		if (previous < 0) {
			previous = 0;
		}
		setAttribute("offset", offset);
		setAttribute("previous", previous);
		setAttribute("next", offset + 1);
		
		return forward("list.jsp");
	}
	
	
	/**
	 * 登録画面を表示する
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary view_add() {
		String offset = super.getParam("offset", String.class);
		if (StringUtils.isEmpty(offset)) {
			offset = "0";
		}
		setAttribute("offset", offset);
		UsersEntity entity = new UsersEntity();
		setAttributeOnProperty(entity);
		
		// 登録されているロールをセット
		setSystemRoles(null);
		
		return forward("view_add.jsp");
	}
	
	/**
	 * システムに登録されているロールをセット
	 */
	private void setSystemRoles(UsersEntity user) {
		RolesDao rolesDao = RolesDao.get();
		List<RolesEntity> rolesEntities = rolesDao.selectAll();
		List<Roles> roles = new ArrayList<>();
		for (RolesEntity rolesEntity : rolesEntities) {
			Roles role = new Roles();
			PropertyUtil.copyPropertyValue(rolesEntity, role);
			roles.add(role);
		}
		
		Map<String, Roles> map = new HashMap<String, Roles>();
		for (Roles role : roles) {
			map.put(role.getRoleKey(), role);
		}
		if (user != null) {
			// ユーザのロールを取得
			List<RolesEntity> userRoles = rolesDao.selectOnUserKey(user.getUserKey());
			for (RolesEntity userRolesEntity : userRoles) {
				if (map.containsKey(userRolesEntity.getRoleKey())) {
					Roles role = map.get(userRolesEntity.getRoleKey());
					role.setChecked(true);
				}
			}
		} else {
			// ユーザが指定されていない = 新規登録なので、デフォルト値をチェック(一般ユーザ権限にチェック）
			if (map.containsKey(CommonWebParameter.ROLE_USER)) {
				Roles role = map.get(CommonWebParameter.ROLE_USER);
				role.setChecked(true);
			}
		}
		
		setAttribute("systemRoles", roles);
	}

	/**
	 * 更新画面を表示する
	 * @return
	 * @throws InvalidParamException 
	 */
	@Auth(roles="admin")
	public Boundary view_edit() throws InvalidParamException {
		String offset = super.getParam("offset", String.class);
		if (StringUtils.isEmpty(offset)) {
			offset = "0";
		}
		setAttribute("offset", offset);
		UsersEntity entity = new UsersEntity();
		setAttributeOnProperty(entity);
		
		Integer userId = super.getPathInteger();
		UsersDao dao = UsersDao.get();
		UsersEntity user = dao.selectOnKey(userId);
		if (user == null) {
			return sendError(404, "user is missing.");
		}
		user.setPassword("");
		setAttributeOnProperty(user);
		
		// 登録されているロールをセット
		setSystemRoles(user);
		
		return forward("view_edit.jsp");
	}

	
	
	/**
	 * ユーザを追加
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary create() {
		String offset = super.getParam("offset", String.class);
		if (StringUtils.isEmpty(offset)) {
			offset = "0";
		}
		setAttribute("offset", offset);
		
		UsersEntity user = new UsersEntity();
		Map<String, String> values = getParams();
		List<ValidateError> errors = user.validate(values);
		if (!StringUtils.isEmpty(getParam("password"))) {
			if (!getParam("password").equals(getParam("confirm_password", String.class))) {
				ValidateError error = new ValidateError("knowledge.user.invalid.same.password");
				errors.add(error);
			}
		}
		UsersDao dao = UsersDao.get();
		user = dao.selectOnUserKey(getParam("userKey"));
		if (user != null) {
			ValidateError error = new ValidateError("knowledge.user.mail.exist");
			errors.add(error);
		}
		if (!errors.isEmpty()) {
			setResult(null, errors);
			return forward("view_add.jsp");
		}
		
		// エラーが無い場合のみ登録
		user = super.getParams(UsersEntity.class);
		String[] roles = getRequest().getParameterValues("roles");
		user = UserLogic.get().insert(user, roles, getLoginedUser());
		setAttributeOnProperty(user);
		
		// 登録されているロールをセット
		setSystemRoles(user);
		
		String successMsg = "message.success.insert";
		setResult(successMsg, errors);
		return forward("view_edit.jsp");
		
	}
	
	
	
	/**
	 * ユーザを更新
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary save() {
		String offset = super.getParam("offset", String.class);
		if (StringUtils.isEmpty(offset)) {
			offset = "0";
		}
		setAttribute("offset", offset);
		
		UsersEntity user = new UsersEntity();
		Map<String, String> values = getParams();
		List<ValidateError> errors = new ArrayList<ValidateError>();
		
		if (StringUtils.isEmpty(getParam("password"))) {
			values.put("password", "-");
		} else {
			if (!getParam("password").equals(getParam("confirm_password", String.class))) {
				ValidateError error = new ValidateError("knowledge.user.invalid.same.password");
				errors.add(error);
			}
		}
		errors.addAll(user.validate(values));
		
		UsersDao dao = UsersDao.get();
		user = dao.selectOnKey(getParam("userId", Integer.class));
		if (user == null) {
			ValidateError error = new ValidateError("message.allready.updated");
			errors.add(error);
		} else {
			UsersEntity check = dao.selectOnUserKey(getParam("userKey"));
			if (check != null && check.getUserId().intValue() != user.getUserId().intValue()) {
				ValidateError error = new ValidateError("knowledge.user.mail.exist");
				errors.add(error);
			}
		}
		if (!errors.isEmpty()) {
			setResult(null, errors);
			return forward("view_edit.jsp");
		}
		
		user.setUserKey(getParam("userKey"));
		user.setUserName(getParam("userName"));
		if (!StringUtils.isEmpty(getParam("password"))) {
			user.setPassword(getParam("password"));
			user.setEncrypted(false);
		}
		// エラーが無い場合のみ登録
		String[] roles = getRequest().getParameterValues("roles");
		user = UserLogic.get().update(user, roles, getLoginedUser());
		setAttributeOnProperty(user);
		
		// 登録されているロールをセット
		setSystemRoles(user);
		
		String successMsg = "message.success.update";
		setResult(successMsg, errors);
		return forward("view_edit.jsp");
	}
	
	
	/**
	 * ユーザを削除
	 * @return
	 * @throws Exception 
	 */
	@Auth(roles="admin")
	public Boundary delete() throws Exception {
		String offset = super.getParam("offset", String.class);
		if (StringUtils.isEmpty(offset)) {
			offset = "0";
		}
		setAttribute("offset", offset);
		UsersDao dao = UsersDao.get();
		UsersEntity user = dao.selectOnKey(getParam("userId", Integer.class));
		if (user != null) {
			//dao.delete(user.getUserId());
			UserLogic.get().withdrawal(user.getUserId(), true, HttpUtil.getLocale(getRequest()));
		}
		String successMsg = "message.success.delete";
		setResult(successMsg, null);
		return list(Integer.parseInt(offset), "");
	}
	
	/**
	 * ユーザ登録承認待ちの一覧表示
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary accept_list() {
		ProvisionalRegistrationsDao dao = ProvisionalRegistrationsDao.get();
		List<ProvisionalRegistrationsEntity> entries = dao.selectAll();
		setAttribute("entries", entries);
		return forward("accept_list.jsp");
	}
	
	
	/**
	 * ユーザ登録承認
	 * @return
	 */
	@Auth(roles="admin")
	public Boundary accept() {
		String id = getPathInfo();
		if (StringUtils.isEmpty(id)) {
			return accept_list();
		}
		if (id.startsWith("/")) {
			id = id.substring(1);
		}
		
		ProvisionalRegistrationsDao dao = ProvisionalRegistrationsDao.get();
		ProvisionalRegistrationsEntity entity = dao.selectOnKey(id);
		if (entity == null) {
			// 承認する仮登録情報が見つかりませんでした。（他の管理者が承認）
			addMsgWarn("message.allready.updated");
			return accept_list();
		}
		
		UsersEntity user = UserLogic.get().activate(entity);
		if (user != null) {
			addMsgSuccess("message.success.accept");
			
			// 承認されたことを通知
			MailLogic mailLogic = MailLogic.get();
			String url = HttpUtil.getContextUrl(getRequest());
			mailLogic.sendAcceptedAddRequest(entity, url);
		} else {
			//他の管理者がすでに承認していました
			addMsgInfo("message.allready.updated");
		}
		return accept_list();
	}
	
	
}
