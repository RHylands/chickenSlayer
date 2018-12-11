import org.osbot.rs07.api.Equipment;
import org.osbot.rs07.api.filter.AreaFilter;
import org.osbot.rs07.api.filter.NameFilter;
import org.osbot.rs07.api.map.Position;
import org.osbot.rs07.api.model.*;
import org.osbot.rs07.api.ui.EquipmentSlot;
import org.osbot.rs07.api.ui.RS2Widget;
import org.osbot.rs07.api.ui.Skill;
import org.osbot.rs07.script.Script;
import org.osbot.rs07.script.ScriptManifest;
import org.osbot.rs07.utility.ConditionalSleep;
import org.osbot.rs07.api.filter.Filter;
import org.osbot.rs07.api.map.Area;

import java.awt.*;

@ScriptManifest(author = "GearsBy", name = "Simple Chicken Slayer", info = "Just an empty script :(", version = 0.1,
        logo = "")





public final class ChickenSlayer extends Script  {

    enum State {
        FIGHTCHICKENS,
        FINDCHICKENS,
        LOOT,
        CHOPANDCOOK,
        BANK,
        DROP,
        WALKTOBANK,
        WALKTOWOODS,
        WALKTOPEN,
        ERROR
    }

    public boolean cooking;
    public boolean banking;
    public boolean burying;
    public boolean feathers;

    private boolean trainAttack;
    private boolean trainDeffence;
    private boolean trainStrength;

    private NPC currentChicken;

    private Skill[] skills = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER, Skill.COOKING,
                                Skill.WOODCUTTING, Skill.FIREMAKING};

    private static final Area EAST_BANK = new Area(3181, 3435, 3185, 3445);
    private static final Area WEST_BANK = new Area(3250, 3419,3257,3423);
    private static final Area WOODS = new Area(3267, 3336, 3277, 3343);
    private static final Area CHICKEN_PEN = new Area(3225, 3295, 3237, 3301);

    private long startTime;


    Filter<Item> keepFilter = new Filter<Item>() {
        @Override
        public boolean match(Item i) {
            return (i.getName().endsWith("axe") || i.getName().contentEquals("Tinderbox") || i.getName().contentEquals("Feathers"));
        }
    };




    @Override
    public final void onStart() {

        this.cooking = true;
        this.banking = true;
        this.burying = true;
        this.feathers = true;

        startTime = System.currentTimeMillis();

        if(this.cooking){
            if(!equipment.isWearingItem(EquipmentSlot.WEAPON, axe -> axe.getName().endsWith("axe")) &&
                    !getInventory().contains(axe -> axe.getName().endsWith("axe"))){
                log("No axe to cut wood :( ");
                this.cooking = false;
            }
            if(!getInventory().contains("Tinderbox")){
                log("No tinderbox to make fire");
                this.cooking = false;
            }
        }

        if(this.banking && !this.cooking){
            log("What to bank if we're not cooking");
            this.banking = false;
        }

        getExperienceTracker().start(Skill.HITPOINTS);
        getExperienceTracker().start(Skill.ATTACK);
        getExperienceTracker().start(Skill.DEFENCE);
        getExperienceTracker().start(Skill.STRENGTH);

        if(this.burying){
            getExperienceTracker().start(Skill.PRAYER);
        }
        if(this.cooking){
            getExperienceTracker().start(Skill.COOKING);
            getExperienceTracker().start(Skill.WOODCUTTING);
            getExperienceTracker().start(Skill.FIREMAKING);
        }


    }

    @Override
    public final int onLoop() throws InterruptedException {

        switch(getState()) {
            case FIGHTCHICKENS:
                log("Fighting a Chicken");
                break;

            case FINDCHICKENS:
                log("Looking for a Chicken");
                findAndAttackChicken();
                break;

            case LOOT:
                log("Loot time");
                loot();
                break;

            case CHOPANDCOOK:
                log("Chop wood -> Cook chicken");
                chopAndCook();
                break;
            case DROP:
                log("Dropping");
                drop();
                break;
            case BANK:
                log("Banking");
                bank();
                break;
            case WALKTOBANK:
                log("Walking to bank");
                walkToBank();
                break;
            case WALKTOPEN:
                log("Walking back to chicken pen");
                walkToChickenPen();
                break;
            case WALKTOWOODS:
                log("Walking to woods");
                walkToWoods();
                break;
            case ERROR:
                log("Landed in error");
                stop(false);
                break;
        }


    return random(1000,3000);

    }


    @Override
    public final void onExit() {
        log("This will be printed to the logger when the script exits");
    }

    @Override
    public void onPaint(final Graphics2D g) {
        g.setFont(g.getFont().deriveFont(10.0f));
        g.setColor(Color.BLACK);
        g.fillRect(10,75,175,210);

        String time = "Running: " + getRunTime();

        int skillCount = 0;

        g.setColor(Color.WHITE);
        g.drawString(time, 15, 85);
        int x = 35;
        int y = 125;

        g.drawString("Exp gained (Levels)", 35,105);

        for(int i = 0; i<8; i++){

            String skillText = skills[i].toString() + " : " + getExperienceTracker().getGainedXP(skills[i]) + " ("
                    + getExperienceTracker().getGainedLevels(skills[i]) + ")";
            g.drawString(skillText, x, y);
            y += 20;
        }
    }

    private State getState(){

        if(getCombat().isFighting()){
            return State.FIGHTCHICKENS;
        }

        if(inChickenPen()){
            if(getCombat().isFighting()){
                return State.FIGHTCHICKENS;
            } else if(!this.cooking && !this.burying){
                //not burying or cooking
                return State.FINDCHICKENS;
            }
            if (this.cooking ){
                //cooking (burying handled in loot
                if (currentChicken == null){
                    if(getInventory().getEmptySlotCount() < 2){
                        return State.WALKTOWOODS;
                    } else {
                        return State.FINDCHICKENS;
                    }
                } else {
                    return State.LOOT;
                }


            } else if (!this.cooking && this.burying){
                //not cooking but burying
                if(currentChicken == null){
                    return State.FINDCHICKENS;
                } else {
                    return State.LOOT;
                }
            }

        }

        if(inWoods()){
            if(getInventory().contains("Raw chicken")){
                return State.CHOPANDCOOK;
            } else if (getInventory().contains("Burnt chicken") || getInventory().contains("Cooked chicken")){
                if(this.banking){
                    return State.WALKTOBANK;
                } else {
                    return State.DROP;
                }
            } else {
                return State.WALKTOPEN;
            }
        }

        if(inBank()){
            if(getInventory().contains("Feathers") || getInventory().contains("Cooked chicken") || getInventory().contains("Burnt chicken")){
                return State.BANK;
            } else {
                return State.WALKTOPEN;
            }
        }

        //Not in given area
        if(getInventory().getEmptySlotCount() == 0){
            if(inventory.contains("Cooked chicken") && this.banking){
                return State.WALKTOBANK;
            }
        } else if (getInventory().getEmptySlotCount() == 1 && this.cooking){
            return State.WALKTOWOODS;
        } else {
            return State.WALKTOPEN;
        }

        return State.ERROR;

    }

    private boolean inChickenPen(){
        return CHICKEN_PEN.contains(myPlayer());
    }

    private boolean inWoods(){
        return WOODS.contains(myPlayer());
    }

    private boolean inBank(){
        return (EAST_BANK.contains(myPlayer()) || WEST_BANK.contains(myPlayer()));
    }

    private void bank(){
        if(inBank()){
            if(!getBank().isOpen()) {

                NPC banker = npcs.closest("Banker");
                if(banker != null){
                    banker.interact("Bank");
                    new ConditionalSleep(3000){
                        @Override
                        public boolean condition() {
                            return (getBank().isOpen());
                        }
                    }.sleep();
                }
            }

            if(getBank().isOpen()){
                getBank().depositAllExcept("Tinderbox", "Bronze axe", "Iron axe", "Steel axe", "Black axe",
                        "Mithril axe", "Adamant axe", "Rune axe", "Dragon axe");
                new ConditionalSleep(3000){
                    @Override
                    public boolean condition() throws InterruptedException {
                        return!(getInventory().contains("Cooked chicken") || getInventory().contains("Burnt chicken")
                        || getInventory().contains("Feathers"));
                    }
                }.sleep();

                if(getBank().isOpen()){
                    getBank().close();
                    new ConditionalSleep(2000){
                        @Override
                        public boolean condition() throws InterruptedException {
                            return getBank().isOpen();
                        }
                    }.sleep();
                }
            }
        }
    }

    private void loot(){

        if(currentChicken != null && currentChicken.getHealthPercent() == 0) {
            log("Chicken we are tracking is dead");
        } else {
            log("LOOT -> chicken we are tracking is not dead?");
            currentChicken = null;
            return;
        }

        if(currentChicken.exists()){
            log("Waiting while chicken does its dying animation");
            new ConditionalSleep(5000, 250) {
                @Override
                public boolean condition() throws InterruptedException {
                    return (!currentChicken.exists()) ;
                }
            }.sleep();
            log("Dead chicken");
        }

        Area corpse = currentChicken.getArea(1);

        if(this.feathers) {
            log("Looting feathers");
            if (getInventory().getEmptySlotCount() > 0 || getInventory().contains("Feathers")) {

                GroundItem feather = groundItems.closest(new AreaFilter<GroundItem>(corpse), new NameFilter<GroundItem>("Feather"));
                if(feather != null){
                    log("Found feathers");
                    feather.interact("Take");
                    new ConditionalSleep(3000, 250) {
                        @Override
                        public boolean condition() throws InterruptedException {
                            return (!feather.exists()) ;
                        }
                    }.sleep();
                } else {
                    log("Didn't find feathers");
                }
            }
        }

        if(this.burying){
            log("Pick up bones");
            if(getInventory().getEmptySlotCount() > 0){
                GroundItem bones = groundItems.closest(new AreaFilter<GroundItem>(corpse), new NameFilter<GroundItem>("Bones"));
                if(bones != null){
                    log("Found bones");
                    bones.interact("Take");
                    new ConditionalSleep(3000, 250) {
                        @Override
                        public boolean condition() throws InterruptedException {
                            return (!bones.exists()) ;
                        }
                    }.sleep();

                    while(inventory.contains("Bones")) {
                        log("Bury the bones");
                        inventory.interact("Bury", "Bones");
                        new ConditionalSleep(2000, 250) {
                            @Override
                            public boolean condition() throws InterruptedException {
                                return myPlayer().isAnimating() ;
                            }
                        }.sleep();

                    }

                }
            }

        }

        if(this.cooking) {
            log("Looting raw chicken");
            if (getInventory().getEmptySlotCount() > 1) { //need room for wood
                log("Have room to loot chicken");
                GroundItem rawChicken = getGroundItems().closest(new AreaFilter<GroundItem>(corpse), new NameFilter<GroundItem>("Raw chicken"));
                if (rawChicken != null) {
                    log("Found raw chicken");
                    rawChicken.interact("Take");
                    new ConditionalSleep(3000, 250) {
                        @Override
                        public boolean condition() throws InterruptedException {
                            return !rawChicken.exists();
                        }
                    }.sleep();
                }
            }
        }

        currentChicken = null;
    }

    private void drop(){
        getInventory().dropAllExcept(keepFilter);
    }

    private void walkToChickenPen(){
        log("Walking back to pen");
        getWalking().webWalk(CHICKEN_PEN); //Web as gate may be closed
        log("Back at pen");
    }

    private void walkToWoods(){
        log("Walking to woods");
        getWalking().webWalk(WOODS);
        log("At woods");

    }

    private void walkToBank(){
        log("Walking to Bank");
        getWalking().webWalk(WEST_BANK); //add some randomness and go east every so often
        log("At Bank");
    }

    private void chopAndCook(){
        if(!inWoods()){
            log("Not in woods :(");
            return;
        }

        if (!inventory.contains("Tinderbox") || !inventory.contains("Raw chicken") ){
            return;
        }

        if(!(inventory.getEmptySlotCount() > 0)){
            //no room please drop something
        }

        int i = 0;

        while(getInventory().contains("Raw chicken") && (i<5)) {
            log("We have raw chicken to cook");
            i += 1;

            RS2Object fire = getObjects().closest(rs2Object -> rs2Object.getName().equals("Fire"));

            if(!getInventory().contains("Logs") && fire == null) {
                log("Looking for tree to cut");
                Entity tree = objects.closest("Tree");
                if (tree != null) {
                    log("Cutting tree");
                    tree.interact("Chop Down");
                    new ConditionalSleep(25000, 500) {
                        @Override
                        public boolean condition() throws InterruptedException {
                            return getInventory().contains("Logs");
                        }
                    }.sleep();
                } else {
                    //didn't find tree
                    log("Couldn't find tree");
                }
            }

            if (getInventory().contains("Logs")){
                inventory.interact("Use", "Tinderbox");
                log("Selecting tinderbox");
                new ConditionalSleep(2000, 100) {
                    @Override
                    public boolean condition() throws InterruptedException {
                        return (getInventory().isItemSelected());
                    }
                }.sleep();

                inventory.interact("Use", "Logs");
                log("Waiting for fire to light");
                new ConditionalSleep(25000, 500) {
                    @Override
                    public boolean condition() throws InterruptedException {
                        return !getInventory().contains("Logs") && !myPlayer().isAnimating() && !myPlayer().isMoving()
                                ;
                    }
                }.sleep();


            }else {
                log("Don't have any logs");
            }


            log("Looking for our fire");
            if (fire != null && fire.isVisible()){
                log("Found our fire");
                if(getInventory().contains("Raw chicken")){
                    log("Selecting chicken to cook");
                    getInventory().interact("Use", "Raw chicken");
                    new ConditionalSleep(2500, 250) {
                        @Override
                        public boolean condition() throws InterruptedException {
                            return getInventory().isItemSelected();
                        }
                    }.sleep();
                    fire.interact("Use");
                    log("Using chicken on fire");

                    //Wait to get to fire and interact
                    sleepUntilWidget(3000,270,12);

                    RS2Widget optionMenu = getWidgets().get(270, 14);
                    if (optionMenu != null){
                        if(optionMenu.isVisible()) {
                            log("Cook menu open");
                            optionMenu.interact("Cook");
                            log("Cooking chicken");
                            long timer = System.currentTimeMillis();
                            long startTimer = System.currentTimeMillis();
                            long timeOut = 15*1000;//Max 15 seconds to cook single item
                            long maxTimeOut = 3*60*1000;//3 minute max timeout
                            long rawCount = getInventory().getAmount("Raw chicken");
                            int cookLevel = getSkills().getStatic(Skill.COOKING);

                            while (((System.currentTimeMillis() - timer) < timeOut) && (System.currentTimeMillis() - startTimer < maxTimeOut)){
                                if(getInventory().getAmount("Raw chicken") != rawCount){
                                    log("We've used a raw chicken");
                                    rawCount = getInventory().getAmount("Raw chicken");
                                    timer = System.currentTimeMillis();
                                }


                                //level up check
                                if(cookLevel != getSkills().getStatic(Skill.COOKING) || dialogues.inDialogue()){
                                    log("We have leveled up!");
                                    cookLevel = getSkills().getStatic(Skill.COOKING);
                                    if(dialogues.inDialogue()){
                                        dialogues.clickContinue();
                                    }
                                    if (fire.exists() && getInventory().contains("Raw chicken")){
                                        fire.interact("Use");
                                        log("Using chicken on fire");

                                        //Wait to get to fire and interact
                                        sleepUntilWidget(3000,270,14);



                                    } else {
                                        log("Resetting timer");
                                        timer = 0; //force end of while as no more fire :/
                                    }
                                }

                            }
                            log("No longer using fire");
                        }

                    } else{
                        log("Can't find option menu (NULL)");
                    }
                } else {
                    log("No raw chicken in inv");
                }

            } else {
                log("Cound'nt find fire or not visable");
            }

            if(i>4){
                log("Looped too many times trying to cook: ERROR");
            }


        }


    }

    private void sleepUntilWidget(int time, int rootID, int childID){
        new ConditionalSleep(time) {
            @Override
            public boolean condition() throws InterruptedException {
                RS2Widget optionMenu = getWidgets().get(rootID, childID);
                return !(optionMenu == null); // must be a better way?
            }
        }.sleep();
    }

    private void findAndAttackChicken(){


        NPC chicken = npcs.closest(new Filter<NPC>() {
            @Override
            public boolean match (NPC npc) {
                return npc.exists() && npc.getName().equals("Chicken") && npc.isAttackable() && npc.getHealthPercent() >0 && npc.isVisible();
            }
        });
        if (chicken == null){
            log("Found no chickens :(");
        } else {
            if(chicken.isOnScreen()) {
                chicken.interact("Attack");
                currentChicken = chicken;
                log("Fight that chicken");
                new ConditionalSleep(5000, 500) {
                    @Override
                    public boolean condition() throws InterruptedException {
                        log("Waiting to see if we fight a chicken");
                        return (getCombat().isFighting() || !myPlayer().isMoving()) ;
                    }
                }.sleep();
            }
        }
    }

    public final String getRunTime(){
        long ms = System.currentTimeMillis() - startTime;
        long s = ms / 1000, m = s / 60, h = m / 60;
        s %= 60; m %= 60; h %= 24;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

}



