package com.lemty.server.service;

import java.util.*;

import com.lemty.server.domain.Campaign;
import com.lemty.server.domain.Emails;
import com.lemty.server.domain.Engagement;
import com.lemty.server.domain.Mail;
import com.lemty.server.domain.ProspectMetadata;
import com.lemty.server.domain.Step;
import com.lemty.server.repo.CampaignRepository;
import com.lemty.server.repo.EmailsRepository;
import com.lemty.server.repo.EngagementRepository;
import com.lemty.server.repo.MailRepo;
import com.lemty.server.repo.ProspectMetadataRepository;
import com.lemty.server.repo.StepRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TrackingService {
    Logger logger = LoggerFactory.getLogger(TrackingService.class);
    private final CampaignRepository campaignRepository;
    private final ProspectMetadataRepository prospectMetadataRepository;
    private final StepService stepService;
    private final EngagementRepository engagementRepository;
    private final EmailsRepository emailsRepository;
    private final IntentDetectionService intentDetectionService;
    private final StepRepository stepRepository;
    private final MailRepo mailRepo;

    public TrackingService(ProspectMetadataRepository prospectMetadataRepository, StepService stepService, CampaignRepository campaignRepository, EngagementRepository engagementRepository, EmailsRepository emailsRepository, IntentDetectionService intentDetectionService, StepRepository stepRepository, MailRepo mailRepo) {
        this.prospectMetadataRepository = prospectMetadataRepository;
        this.stepService = stepService;
        this.campaignRepository = campaignRepository;
        this.engagementRepository = engagementRepository;
        this.emailsRepository = emailsRepository;
        this.intentDetectionService = intentDetectionService;
        this.stepRepository = stepRepository;
        this.mailRepo = mailRepo;
    }


    public void trackOpens(String emailId){
        Emails email = emailsRepository.findById(emailId).get();
        String prospectId = email.getProspect().getId();
        String campaignId = email.getCampaign().getId();
        Integer stepNumber = email.getStep();
        Integer mailNumber = email.getMail();

        //Set opens in prospect metadata
        ProspectMetadata metadata = prospectMetadataRepository.findByProspectIdAndCampaignId(prospectId, campaignId);

        List<Engagement> engagements = engagementRepository.findByProspectMetadataId(metadata.getId());
        if(engagements.isEmpty()){
            Engagement engagement = new Engagement();
            engagement.setOpens(engagement.getOpens() + 1);
            engagement.setStepNumber(stepNumber + 1);
            engagement.setProspectMetadata(metadata);
            engagement.setCampaign(campaignRepository.getById(campaignId));
            engagements.add(engagement);
            engagementRepository.save(engagement);
            email.setEngagement(engagement);
            emailsRepository.save(email);
        }
        else{
            Engagement existingEngagement = engagementRepository.findByProspectMetadataIdAndStepNumber(metadata.getId(), stepNumber + 1);
            if(existingEngagement != null){
                if(stepNumber + 1 == existingEngagement.getStepNumber()){
                    existingEngagement.setOpens(existingEngagement.getOpens() + 1);
                    engagementRepository.save(existingEngagement);
                }
            }
            else{
                Engagement engagement = new Engagement();
                engagement.setOpens(engagement.getOpens() + 1);
                engagement.setStepNumber(stepNumber + 1);
                engagement.setProspectMetadata(metadata);
                engagements.add(engagement);
                engagement.setCampaign(campaignRepository.getById(campaignId));
                engagementRepository.save(engagement);
                email.setEngagement(engagement);
                emailsRepository.save(email);
            }

        }
        prospectMetadataRepository.save(metadata);

       //Set opens in step
       List<Step> steps = stepService.getStepsFromCampaign(campaignId);
       Campaign campaign = campaignRepository.findById(campaignId).get();

       Step step = steps.get(stepNumber);
       Integer stepOpens = step.getOpens();
       step.setOpens(stepOpens + 1);
       steps.set(stepNumber, step);

       List<Mail> mails = stepService.getMailsFromSteps(step.getId());
       Mail mail = mails.get(mailNumber);

       Integer opens = mail.getOpens();
       mail.setOpens(opens + 1);
       mails.set(mailNumber, mail);

       if(campaign.getTotalOpens() == null){
           campaign.setTotalOpens(1);
       }
       else{
           campaign.setTotalOpens(campaign.getTotalOpens() + 1);
       }
       stepRepository.save(step);
       mailRepo.save(mail);
       campaignRepository.save(campaign);
       intentDetectionService.detectOpenIntent(prospectId);
    }


    public void trackClicks(String emailId){
        Emails email = emailsRepository.findById(emailId).get();
        String prospectId = email.getProspect().getId();
        String campaignId = email.getCampaign().getId();
        Integer stepNumber = email.getStep();
        Integer mailNumber = email.getMail();

        //Set opens in prospect metadata
        ProspectMetadata metadata = prospectMetadataRepository.findByProspectIdAndCampaignId(prospectId, campaignId);
        List<Engagement> engagements = engagementRepository.findByProspectMetadataId(metadata.getId());
        if(engagements.isEmpty()){
            Engagement engagement = new Engagement();
            engagement.setClicks(engagement.getClicks() + 1);
            engagement.setStepNumber(stepNumber + 1);
            engagement.setProspectMetadata(metadata);
            engagement.setCampaign(campaignRepository.getById(campaignId));
            engagements.add(engagement);
            engagementRepository.save(engagement);
            email.setEngagement(engagement);
            emailsRepository.save(email);
        }
        else{
            Engagement existingEngagement = engagementRepository.findByProspectMetadataIdAndStepNumber(metadata.getId(), stepNumber + 1);
            if(existingEngagement != null){
                if(stepNumber + 1 == existingEngagement.getStepNumber()){
                    existingEngagement.setClicks(existingEngagement.getClicks() + 1);
                    engagementRepository.save(existingEngagement);
                }
            }
            else{
                Engagement engagement = new Engagement();
                engagement.setClicks(engagement.getClicks() + 1);
                engagement.setStepNumber(stepNumber + 1);
                engagement.setProspectMetadata(metadata);
                engagement.setCampaign(campaignRepository.getById(campaignId));
                engagements.add(engagement);
                engagementRepository.save(engagement);
                email.setEngagement(engagement);
                emailsRepository.save(email);
            }

        }
        prospectMetadataRepository.save(metadata);

       //Set opens in step
       List<Step> steps = stepService.getStepsFromCampaign(campaignId);
       Campaign campaign = campaignRepository.findById(campaignId).get();

       Step step = steps.get(stepNumber);
       Integer stepClicks = step.getClicks();

       step.setClicks(stepClicks + 1);
       steps.set(stepNumber, step);

       List<Mail> mails = stepService.getMailsFromSteps(step.getId());
       Mail mail = mails.get(mailNumber);

       Integer clicks = mail.getClicks();
       mail.setClicks(clicks + 1);

       if(campaign.getTotalOpens() == null){
           campaign.setTotalClicks(1);
       }
       else{
           campaign.setTotalClicks(campaign.getTotalOpens() + 1);
       }
       stepRepository.save(step);
       mailRepo.save(mail);
       campaignRepository.save(campaign);
       intentDetectionService.detectClickIntent(prospectId);
    }
}
