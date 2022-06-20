package com.lemty.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemty.server.domain.Campaign;
import com.lemty.server.domain.Mail;
import com.lemty.server.domain.Step;
import com.lemty.server.repo.CampaignRepository;
import com.lemty.server.repo.StepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StepService {
    Logger logger = LoggerFactory.getLogger(Campaign.class);
    private final StepRepository stepRepository;
    private final CampaignRepository campaignRepository;
    private final MailService mailService;

    @Autowired
    public StepService(StepRepository stepRepository, CampaignRepository campaignRepository, MailService mailService) {
        this.stepRepository = stepRepository;
        this.campaignRepository = campaignRepository;
        this.mailService = mailService;
    }


    //List all steps
    public List<Step> getAll(){
        return stepRepository.findAll();
    }

    @Transactional
    //List step by campaign
    public List<Step> getStepsFromCampaign(String campaignId){
        Campaign campaign = campaignRepository.findById(campaignId).get();
        return campaign.getSteps();
    }

    //List step by campaign
    public List<Mail> getMailsFromSteps(String stepId){
        List<Mail> mails = mailService.getFromStep(stepId);
        return mails;
    }


    //Add new Step
    public void addNewStep(List<Step> step, String campaignId){
        Campaign campaign = campaignRepository.findById(campaignId).get();

        for(int i=0; i < step.size(); i++){
            step.get(i).setCampaign(campaign);
            List<Mail> mails = step.get(i).getMails();

            stepRepository.saveAndFlush(step.get(i));

            mailService.addNewMail(mails, step.get(i).getId());
        }
        // stepRepository.saveAll(step);
    }

//    //Update Step
//    public void updateStep(Step newStep, String stepId){
//        stepRepository.findById(stepId)
//                .map(step -> {
//                    step.setId(stepId);
//                    step.setStepNumber(newStep.getStepNumber());
//                    step.setDay(newStep.getDay());
//                    step.setDayGap(newStep.getDayGap());
//                    step.setHour(newStep.getHour());
//                    step.setHourGap(newStep.getHourGap());
//                    step.setMinute(newStep.getMinute());
//                    step.setMinuteGap(newStep.getMinuteGap());
//                    step.setDeliveryWindow(newStep.getDeliveryWindow());
//                    // step.setDays(newStep.getDays());
//                    step.setWhichEmail(newStep.getWhichEmail());

//                    return stepRepository.save(step);
//                })
//                .orElseGet(() -> {
//                    return null;
//                });
//    }
    //Delete Step
    public void deleteStep(String stepId){
        stepRepository.deleteById(stepId);
    }
}
