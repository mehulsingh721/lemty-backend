package com.lemty.server.service;

import com.lemty.server.domain.*;
import com.lemty.server.helpers.GmailHelper;
import com.lemty.server.helpers.PlaceholderHelper;
import com.lemty.server.jobs.MailJob;
import com.lemty.server.repo.*;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class MailJobService {
    Logger logger = LoggerFactory.getLogger(MailJobService.class);
    private final GmailHelper gmailHelper;
    private final PlaceholderHelper placeholderHelper;
    private final CampaignRepository campaignRepository;
    private final DeliveribilitySettingsService deliveribilitySettingsService;
    private final EmailSignatureService emailSignatureService;
    private final UnsubscribeService unsubscribeService;
    private final ProspectMetadataRepository prospectMetadataRepository;
    private final Scheduler scheduler;
    private final ProspectRepository prospectRepository;
    private final StepService stepService;
    private final EmailsRepository emailsRepository;
    private final UserRepo userRepo;
    private final EngagementRepository engagementRepository;
    private final StepRepository stepRepository;
    @Autowired
    private Environment env;

    public MailJobService(ProspectService prospectService, GmailHelper gmailHelper, PlaceholderHelper placeholderHelper, DeliveribilitySettingsService deliveribilitySettingsService, CampaignRepository campaignRepository, EmailSignatureService emailSignatureService, UnsubscribeService unsubscribeService, ProspectMetadataRepository prospectMetadataRepository, Scheduler scheduler, StepService stepService, ProspectRepository prospectRepository, EmailsRepository emailsRepository, UserRepo userRepo, EngagementRepository engagementRepository, StepRepository stepRepository) {
        this.gmailHelper = gmailHelper;
        this.placeholderHelper = placeholderHelper;
        this.deliveribilitySettingsService = deliveribilitySettingsService;
        this.campaignRepository = campaignRepository;
        this.emailSignatureService = emailSignatureService;
        this.unsubscribeService = unsubscribeService;
        this.prospectMetadataRepository = prospectMetadataRepository;
        this.scheduler = scheduler;
        this.stepService = stepService;
        this.prospectRepository = prospectRepository;
        this.emailsRepository = emailsRepository;
        this.userRepo = userRepo;
        this.engagementRepository = engagementRepository;
        this.stepRepository = stepRepository;
    }
    public void runStep(List<String> prospectIds, String campaignId, Integer stepNumber, Integer nextStepNumber, String userId, ZonedDateTime startDate) {
        //Fetching all data needed
        Campaign campaign = campaignRepository.findById(campaignId).get();
        List<Step> steps = stepService.getStepsFromCampaign(campaignId);
        List<EmailSignature> signatures = emailSignatureService.getSignatures(userId);
        AppUser user = userRepo.findById(userId).get();
        Unsubscribe unsubscribe = unsubscribeService.getUnsubscribe(userId);
        Step step = stepRepository.findByCampaignIdAndStepNumber(campaignId, stepNumber);
        List<Mail> mails = stepService.getMailsFromSteps(step.getId());

        //Scheduling parameters
        DeliveribilitySettings deliveribilitySettings = deliveribilitySettingsService.getDeliveribilitySettings(userId);
        String window = String.valueOf(step.getStartHour()) + "-" + String.valueOf(step.getEndHour());

        String[] days = step.getDays();
        List<String> list = new ArrayList<String>(Arrays.asList(days));
        StringJoiner joiner = new StringJoiner(",");
        for(int l = 0; l < list.size(); l++){
            joiner.add(list.get(l).toString());
        }
        String str = joiner.toString();

        //Sending next step to prospect
        Step afterNextStep = stepRepository.findByCampaignIdAndStepNumber(campaignId, nextStepNumber + 1);
        Integer afterNextStepNumber;
        if(afterNextStep != null){
            afterNextStepNumber = afterNextStep.getStepNumber();
        }
        else{
            afterNextStepNumber = null;
        }

        List<Emails> initiEmails = new ArrayList<>();

        ZonedDateTime currentZonedDateTime = ZonedDateTime.now();
        for(int i=0; i < prospectIds.size(); i++){
            //Mail Data
            Prospect prospect = prospectRepository.findById(prospectIds.get(i)).get();
            ProspectMetadata prospectMetadata = prospectMetadataRepository.findByProspectIdAndCampaignId(prospect.getId(), campaignId);
            if(prospectMetadata.getLastCompletedStep() == stepNumber){
                return;
            }
            else{
                if(!prospect.getUnsubscribed()){
                    String from = step.getWhichEmail();
                    String to = prospect.getProspectEmail();
                    String subject = mails.get(i % mails.size()).getSubject();
                    String body = mails.get(i % mails.size()).getBody();
                    subject = placeholderHelper.fieldsReplacer(subject, prospect);
                    body = placeholderHelper.fieldsReplacer(body, prospect);
                    Emails email = new Emails();
                    body = placeholderHelper.bodyLinkReplacer(body, email.getId());

                    if(signatures.size() > 0){
                        body = placeholderHelper.signatureReplacer(body, signatures.get(0));
                    }
                    if(unsubscribe != null){
                        body = placeholderHelper.unsubLinkReplacer(body, prospect.getId(), unsubscribe);
                    }
                    email.setFromEmail(from);
                    email.setToEmail(to);
                    email.setSubject(subject);
                    email.setAppUser(user);
                    email.setCampaign(campaign);
                    email.setStep(stepNumber);
                    email.setProspect(prospect);
                    email.setMail(i % mails.size());

                    Engagement engagement = new Engagement();
                    engagement.setOpens(engagement.getOpens() + 1);
                    engagement.setStepNumber(stepNumber + 1);
                    engagement.setCampaign(campaignRepository.getById(campaignId));
                    engagementRepository.save(engagement);
                    email.setEngagement(engagement);
                    emailsRepository.save(email);

                    String openLink =  env.getProperty("track.url").toString() + "/getAttachment/" + email.getId();
                    body = body + "<img src='" + openLink + "' alt=''>";

                    email.setBody(body);
                    initiEmails.add(email);

                    String nextProspectId = "";
                    if((i + 1) == prospectIds.size()){
                        nextProspectId = "";
                    }
                    else{
                        nextProspectId = prospectIds.get(i + 1);
                    }
                    logger.info(nextProspectId);

                    try {
                        JobDetail jobDetail = buildMailJobDetail(campaignId, prospect.getId(), stepNumber, userId, nextStepNumber, afterNextStepNumber, email.getId(), nextProspectId);
                        scheduler.addJob(jobDetail, true);

                    } catch (SchedulerException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        emailsRepository.saveAll(initiEmails);

        //Trigger mail job
        JobKey jobKey = new JobKey(initiEmails.get(0).getId() + "-" + campaignId, campaignId);
        logger.info(String.valueOf(Date.from(startDate.toInstant().atZone(ZoneId.of(campaign.getTimezone())).toInstant())));
        logger.info(String.valueOf(Date.from(startDate.toInstant())));
        try {
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            logger.info(String.valueOf(jobDetail));
            Trigger trigger = buildMailTrigger(jobDetail, str, campaignId, campaign.getTimezone(), window, startDate);
            scheduler.scheduleJob(trigger);
            Date emailStartDateTime = trigger.getFireTimeAfter(Date.from(startDate.toInstant()));
            if(DateUtils.isSameDay(Date.from(currentZonedDateTime.toInstant()), emailStartDateTime)){
                List<Emails> emails = emailsRepository.findByCampaignIdAndStatus(campaignId, "TODAY");
                for(Emails email2 : emails){
                    email2.setStartTime(emailStartDateTime);
                    email2.setStatus("TODAY");
                }
                emailsRepository.saveAll(emails);
            }
            else{
                List<Emails> emails = emailsRepository.findByCampaignIdAndStatus(campaignId, "TODAY");
                for(Emails email2 : emails){
                    email2.setStatus("UPCOMING");
                }
                emailsRepository.saveAll(emails);
            }
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }

    private JobDetail buildMailJobDetail(String campaignId, String prospectId, Integer stepNumber, String userId, Integer nextStepNumber, Integer afterNextStepNumber, String emailsId, String nextProspectId){
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("campaignId", campaignId);
        jobDataMap.put("prospectId", prospectId);
        jobDataMap.put("stepNumber", stepNumber);
        jobDataMap.put("userId", userId);
        jobDataMap.put("nextStepNumber", nextStepNumber);
        jobDataMap.put("afterNextStepNumber", afterNextStepNumber);
        jobDataMap.put("emailsId", emailsId);
        jobDataMap.put("nextProspectId", nextProspectId);

        return JobBuilder.newJob(MailJob.class)
                .withIdentity(emailsId + "-" + campaignId, campaignId)
                .withDescription("Mail Job")
                .storeDurably()
                .usingJobData(jobDataMap)
                .build();
    }

    private Trigger buildMailTrigger(JobDetail jobDetail, String days, String campaignId, String timezone,  String window, ZonedDateTime startDate){
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobDetail.getKey().getName(), campaignId)
                .withDescription("Mail Job")
                .startAt(Date.from(startDate.toInstant().atZone(ZoneId.of(timezone)).toInstant()))
                .withSchedule(CronScheduleBuilder
                        .cronSchedule("5 * " + window + "  ? * " + days)
                        .inTimeZone(TimeZone.getTimeZone(timezone))
                        .withMisfireHandlingInstructionFireAndProceed()
                )
                .build();
    }

}
