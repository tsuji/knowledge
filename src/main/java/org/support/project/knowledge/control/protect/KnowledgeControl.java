package org.support.project.knowledge.control.protect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.support.project.common.bean.ValidateError;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.StringUtils;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.control.KnowledgeControlBase;
import org.support.project.knowledge.dao.CommentsDao;
import org.support.project.knowledge.dao.KnowledgesDao;
import org.support.project.knowledge.entity.CommentsEntity;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.TagsEntity;
import org.support.project.knowledge.logic.GroupLogic;
import org.support.project.knowledge.logic.KnowledgeLogic;
import org.support.project.knowledge.logic.UploadedFileLogic;
import org.support.project.knowledge.vo.UploadFile;
import org.support.project.web.boundary.Boundary;
import org.support.project.web.common.HttpStatus;
import org.support.project.web.entity.GroupsEntity;
import org.support.project.web.exception.InvalidParamException;

@DI(instance=Instance.Prototype)
public class KnowledgeControl extends KnowledgeControlBase {
	/** ログ */
	private static Log LOG = LogFactory.getLog(KnowledgeControl.class);
	
	private KnowledgeLogic knowledgeLogic = KnowledgeLogic.get();
	private UploadedFileLogic fileLogic = UploadedFileLogic.get();
	
	/**
	 * 登録画面を表示する
	 * @return
	 */
	public Boundary view_add() {
		// 共通処理呼の表示条件の保持の呼び出し
		setViewParam();
		
		String offset = super.getParam("offset", String.class);
		if (StringUtils.isEmpty(offset)) {
			offset = "0";
		}
		setAttribute("offset", offset);

		return forward("view_add.jsp");
	}
	/**
	 * 更新画面を表示する
	 * @return
	 * @throws InvalidParamException 
	 */
	public Boundary view_edit() throws InvalidParamException {
		// 共通処理呼の表示条件の保持の呼び出し
		setViewParam();
		
		String offset = super.getParam("offset", String.class);
		if (StringUtils.isEmpty(offset)) {
			offset = "0";
		}
		setAttribute("offset", offset);
		
		Long knowledgeId = super.getPathLong();
		KnowledgesEntity entity = knowledgeLogic.selectWithTags(knowledgeId, getLoginedUser());
		if (entity == null) {
			return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
		}
		setAttributeOnProperty(entity);
		
		// ナレッジに紐づく添付ファイルを取得
		List<UploadFile> files = fileLogic.selectOnKnowledgeId(knowledgeId, getRequest().getContextPath());
		setAttribute("files", files);
		
		// 表示するグループを取得
		List<GroupsEntity> groups = GroupLogic.get().selectGroupsOnKnowledgeId(knowledgeId);
		setAttribute("groups", groups);
		
		if (!super.getLoginedUser().isAdmin() && entity.getInsertUser().intValue() != super.getLoginUserId().intValue()) {
			addMsgWarn("knowledge.edit.noaccess");
			return forward("/open/knowledge/view.jsp");
		}
		
		return forward("view_edit.jsp");
	}
	
	/**
	 * 登録する
	 * @return
	 * @throws Exception 
	 */
	public Boundary add(KnowledgesEntity entity) throws Exception {
		// 共通処理呼の表示条件の保持の呼び出し
		setViewParam();
		
		String groupsstr = super.getParam("groups");
		String[] groupssp = groupsstr.split(",");
		List<GroupsEntity> groups = GroupLogic.get().selectGroups(groupssp);
		setAttribute("groups", groups);

		List<Long> fileNos = new ArrayList<Long>();
		Object obj = getParam("files", Object.class);
		if (obj != null) {
			if (obj instanceof String) {
				String string = (String) obj;
				if (StringUtils.isLong(string)) {
					fileNos.add(new Long(string));
				}
			} else if (obj instanceof List) {
				List<String> strings = (List<String>) obj;
				for (String string : strings) {
					if (StringUtils.isLong(string)) {
						fileNos.add(new Long(string));
					}
				}
			}
		}

		
		List<ValidateError> errors = entity.validate();
		if (!errors.isEmpty()) {
			setResult(null, errors);
			// バリデーションエラーが発生した場合、設定されていた添付ファイルの情報は再取得
			List<UploadFile> files = fileLogic.selectOnFileNos(fileNos, getRequest().getContextPath());
			Iterator<UploadFile> iterator = files.iterator();
			while (iterator.hasNext()) {
				UploadFile uploadFile = (UploadFile) iterator.next();
				if (uploadFile.getKnowlegeId() != null) {
					// 新規登録なのに、添付ファイルが既にナレッジに紐づいている（おかしい）
					iterator.remove();
				}
			}
			setAttribute("files", files);

			return forward("view_add.jsp");
		}
		LOG.trace("save");
		String tags = super.getParam("tagNames");
		List<TagsEntity> tagList = knowledgeLogic.manegeTags(tags);
		
		entity = knowledgeLogic.insert(entity, tagList, fileNos, groups, super.getLoginedUser());
		setAttributeOnProperty(entity);
		
		List<UploadFile> files = fileLogic.selectOnKnowledgeId(entity.getKnowledgeId(), getRequest().getContextPath());
		setAttribute("files", files);
		
		addMsgSuccess("message.success.insert");
		return forward("view_edit.jsp");
	}
	
	/**
	 * 更新する
	 * @return
	 * @throws Exception 
	 */
	public Boundary update(KnowledgesEntity entity) throws Exception {
		// 共通処理呼の表示条件の保持の呼び出し
		setViewParam();
		
		String groupsstr = super.getParam("groups");
		String[] groupssp = groupsstr.split(",");
		List<GroupsEntity> groups = GroupLogic.get().selectGroups(groupssp);
		setAttribute("groups", groups);
		
		List<Long> fileNos = new ArrayList<Long>();
		Object obj = getParam("files", Object.class);
		if (obj != null) {
			if (obj instanceof String) {
				String string = (String) obj;
				if (StringUtils.isLong(string)) {
					fileNos.add(new Long(string));
				}
			} else if (obj instanceof List) {
				List<String> strings = (List<String>) obj;
				for (String string : strings) {
					if (StringUtils.isLong(string)) {
						fileNos.add(new Long(string));
					}
				}
			}
		}

		KnowledgesDao dao = Container.getComp(KnowledgesDao.class);
		List<ValidateError> errors = entity.validate();
		if (!errors.isEmpty()) {
			setResult(null, errors);
			
			// バリデーションエラーが発生した場合、設定されていた添付ファイルの情報は再取得
			List<UploadFile> files = fileLogic.selectOnFileNos(fileNos, getRequest().getContextPath());
			Iterator<UploadFile> iterator = files.iterator();
			while (iterator.hasNext()) {
				UploadFile uploadFile = (UploadFile) iterator.next();
				if (uploadFile.getKnowlegeId() != null 
						&& uploadFile.getKnowlegeId().longValue() != entity.getKnowledgeId().longValue()) {
					// ナレッジIDが空でなく、かつ、更新中のナレッジ以外に紐づいている添付ファイルはおかしいので削除
					iterator.remove();
				}
			}
			setAttribute("files", files);
			
			return forward("view_edit.jsp");
		}
		
		KnowledgesEntity check = dao.selectOnKey(entity.getKnowledgeId());
		if (check == null) {
			return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
		}
		if (!super.getLoginedUser().isAdmin() && check.getInsertUser().intValue() != super.getLoginUserId().intValue()) {
			addMsgWarn("knowledge.edit.noaccess");
			return forward("/open/knowledge/view.jsp");
		}
		
		LOG.trace("save");
		String tags = super.getParam("tagNames");
		List<TagsEntity> tagList = knowledgeLogic.manegeTags(tags);
		
		entity = knowledgeLogic.update(entity, tagList, fileNos, groups, super.getLoginedUser());
		setAttributeOnProperty(entity);
		addMsgSuccess("message.success.update");
		
		List<UploadFile> files = fileLogic.selectOnKnowledgeId(entity.getKnowledgeId(), getRequest().getContextPath());
		setAttribute("files", files);
		
		return forward("view_edit.jsp");
	}
	
	/**
	 * ナレッジを削除
	 * @return
	 * @throws Exception
	 */
	public Boundary delete() throws Exception {
		// 共通処理呼の表示条件の保持の呼び出し
		setViewParam();
		
		LOG.trace("validate");
		KnowledgesDao dao = Container.getComp(KnowledgesDao.class);
		String id = getParam("knowledgeId");
		if (!StringUtils.isInteger(id)) {
			// 削除するIDが指定されていない
			//return sendError(HttpStatus.SC_400_BAD_REQUEST, null);
			addMsgError("knowledge.delete.none");
			//return super.devolution("open.knowledge/list");
			return forward("/commons/errors/server_error.jsp");
		}
		Long knowledgeId = new Long(id);
		KnowledgesEntity check = dao.selectOnKey(knowledgeId);
		if (check == null) {
			return sendError(HttpStatus.SC_404_NOT_FOUND, "NOT_FOUND");
		}
		if (!super.getLoginedUser().isAdmin() && check.getInsertUser().intValue() != super.getLoginUserId().intValue()) {
			addMsgWarn("knowledge.edit.noaccess");
			return forward("/open/knowledge/view.jsp");
		}
		LOG.trace("save");
		knowledgeLogic.delete(knowledgeId, getLoginedUser());
		
		addMsgSuccess("message.success.delete");
		return super.devolution("open.knowledge/list");
	}
	
	/**
	 * ログイン後、表示しなおし
	 * @return
	 * @throws InvalidParamException
	 */
	public Boundary view() throws InvalidParamException {
		// 共通処理呼の表示条件の保持の呼び出し
		setViewParam();
		
		Long knowledgeId = super.getPathLong(Long.valueOf(-1));
		return super.redirect(getRequest().getContextPath() + "/open.knowledge/view/" + knowledgeId);
	}
	
	/**
	 * コメント追加
	 * @return
	 * @throws InvalidParamException
	 */
	public Boundary comment() throws InvalidParamException {
		// 共通処理呼の表示条件の保持の呼び出し
		String params = setViewParam();
		
		Long knowledgeId = super.getPathLong(Long.valueOf(-1));
		
		String comment = getParam("comment");
		CommentsDao commentsDao = CommentsDao.get();
		CommentsEntity commentsEntity = new CommentsEntity();
		commentsEntity.setKnowledgeId(knowledgeId);
		commentsEntity.setComment(comment);
		commentsDao.insert(commentsEntity);
		
		// 一覧表示用の情報を更新
		KnowledgeLogic.get().updateKnowledgeExInfo(knowledgeId);
		
		return super.redirect(getRequest().getContextPath() + "/open.knowledge/view/" + knowledgeId + params);
	}
	
}




