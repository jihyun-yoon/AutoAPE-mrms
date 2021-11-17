package com.seculayer.mrms.checker;

import com.seculayer.mrms.common.Constants;
import com.seculayer.mrms.db.ProjectManageDAO;
import com.seculayer.mrms.info.LearnInfo;
import com.seculayer.mrms.request.Request;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;

import java.util.List;
import java.util.Map;

public class ProjectCompleteChecker extends Checker {
    private ProjectManageDAO dao = new ProjectManageDAO();

    @Override
    public void doCheck() throws CheckerException {
        List<Map<String, Object>> recommendingProjectList = dao.selectProjectSchedule(Constants.STATUS_PROJECT_LEARN_ING);

        for (Map<String, Object> idMap : recommendingProjectList) {
            List<Map<String, Object>> schedules = dao.selectLearningModelList(idMap);
            int cntModel = schedules.size();

            int tmpCnt = 0;
            for (Map<String, Object> schd: schedules) {
                if (schd.get("learn_sttus_cd").toString().equals(Constants.STATUS_LEARN_COMPLETE)) {
                    tmpCnt++;
                }
            }

            if (cntModel == tmpCnt) {
                // 완료 상태 업데이트
                idMap.replace("status", Constants.STATUS_PROJECT_COMPLETE);
                dao.updateStatus(idMap);
                this.deleteKubeResources(idMap, schedules);
            }
        }
    }

    public void deleteKubeResources(Map<String, Object> idMap, List<Map<String, Object>> modelList) {
        // delete job
        String projectID = idMap.get("project_id").toString();

        try {
            Request.deleteJob(String.format("dprs-%s-0", projectID));
            Request.deleteJob(String.format("hprs-%s-0", projectID));
            Request.deleteJob(String.format("mars-%s-0", projectID));

            V1PodList podList = Request.getPodList();

            for (V1Pod item : podList.getItems()) {
                String podName = item.getMetadata().getName();
                if (podName.contains(String.format("dprs-%s-0",projectID)) ||
                        podName.contains(String.format("hprs-%s-0",projectID)) ||
                        podName.contains(String.format("mars-%s-0",projectID))) {
                    Request.deletePod(podName);
                }
            }

            for(Map<String, Object> model: modelList) {
                String modelHistNo = model.get("learn_hist_no").toString();
                LearnInfo loadedInfo = new LearnInfo(modelHistNo);
                loadedInfo.loadInfo(modelHistNo);
                int numWorker = loadedInfo.getNumWorker();

                for(int i=0; i<numWorker; i++){
                    String name = String.format("learn-%s-%s", modelHistNo, i);
                    Request.deleteJob(name);
                    Request.deleteService(name);

                    for (V1Pod item : podList.getItems()) {
                        String podName = item.getMetadata().getName();
                        if (podName.contains(name)) {
                            Request.deletePod(podName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
