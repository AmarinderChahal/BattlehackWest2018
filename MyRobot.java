package robot;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.lang.Math;

public class MyRobot extends BCAbstractRobot {
    
    /* Directions:
       3 2 1
       4   0
       5 6 7
    */
    private Robot me;
    private int id = -1;
    private int dir = -1;
    private int x, y;
    private int my_health;
    private boolean defend = false;
    private boolean attack = false;
    private boolean inBattle = false;
    
    private HashSet<Integer> visibleAllies = new HashSet<>();
    private HashSet<Integer> visibleEnemies = new HashSet<>();
    // private HashSet<Integer> visibleUnknown = new HashSet<>();
    
    private HashMap<Integer, Integer> visibleAllyHealth = new HashMap<>();
    
    private int[][] map;
    private boolean inLattice = false;
    private boolean inPartial = false;
    private int densityCounter = 0;
    
    private int healthThresh = 30;

    public Action turn() {
        try{
            me = me();
            id = me.id;
            x = me().x;
            y = me().y;
            if (dir == -1) {
                setInitialDirection();
            }
            
            scan();
            me().signal = signalFunction();
            signal(me().signal);
            
            map = getVisibleMap();
            checkPartial();
            checkLattice();
            checkDisruption();
            
            defend = inLattice;
            
            if (timeToRun()) {
                int temp = runAway();
                if (temp != -1) return move(temp);
            }
            
            if(visibleAllies.size()<4) return randDir();
            
            if (visibleEnemies.size() > 0) {
                Action temp = checkAttack();
                if (temp != null) {
                    return temp;   
                }
            }
            
            if (visibleAllies.size() > 17) {
                int direction = friendCOM();
                int temp_dir = chooseDirection((direction+4)%8);
                if (temp_dir == -1) return null;
                return move(temp_dir);
            }
            
            // if(stayInMiddle())return null;
            
            if(!inLattice && !inPartial){
                
                if(disruptingNexus()){
                    // log("atem com");
                    int dir = closestDirTo(friendCOM()+4);
                    if(dir!=-1)return move(dir);
                    // log("no com");
                }
                
                ArrayList<int[]> parts = findPartialsInVis(3);
                if(parts.size()==0)parts = findPartialsInVis(2);
                if(parts.size()==0)parts = findFriendlies();
                if(parts.size()==0)return randDir();
                
                double cosDist = 10000;
                int[] closest = new int[2];
                for(int i = 0; i<parts.size(); i++){
                    int[] coord = parts.get(i);
                    double dist = distanceTo(coord[0]-3,coord[1]-3);
                    if(dist<cosDist){
                        cosDist = dist;
                        closest = coord;
                    }
                    if(Math.abs(dist-cosDist)<.1){
                        if(Math.random()>.5){
                            cosDist = dist;
                            closest = coord;
                        }
                    }
                }
                int dir = directionTo(closest[0]-3,closest[1]-3);
                // log(""+closest[0]+" "+closest[1]);
                if(dir != -1) {
                    dir = closestDirTo(dir);
                    if(dir != -1) return move(closestDirTo(dir));
                }
            }
            
            if(!inLattice && inPartial){
                int currLev = findConnLevel();
                ArrayList<int[]> parts = new ArrayList<int[]>(1);
                for(int i = 3; i >= currLev; i--){
                    parts = findBetterPartial(i);
                    if(parts.size()>0) break;
                }
                if(parts.size()>0){
                    double cosDist = 10000;
                    int[] closest = new int[2];
                    for(int i = 0; i<parts.size(); i++){
                        int[] coord = parts.get(i);
                        double dist = distanceTo(coord[0]-3,coord[1]-3);
                        if(dist<cosDist){
                            cosDist = dist;
                            closest = coord;
                        }
                        if(Math.abs(dist-cosDist)<.1){
                            if(Math.random()>.5){
                                cosDist = dist;
                                closest = coord;
                            }
                        }
                    }
                    int dir = directionTo(closest[0]-3,closest[1]-3);
                    // log(""+closest[0]+" "+closest[1]);
                    if(dir != -1){
                        dir = closestDirTo(dir);
                        if(dir != -1) return move(closestDirTo(dir));
                    }
                }
                // otherwise stay in partial
            }
           
            if(inLattice){
                Action dor = densityOverride();
                if(dor!=null) return dor;
            }
        }catch(Exception e){
            log("oops");
        }
        
        return null;
        // return move(chooseDirection());
    }
    
    /*
     * Check current health and determines if bot should retreat 
     * @return  boolean: true if healh drops below 8, false otherwise
     */
    private boolean timeToRun() {
        if (my_health > me().health && me().health < 8) {
            return true;
        }
        return false;
    }
    
    /*
     * Returns direction from the center of mass of visible allies
     * @return  int: directions away from mass of allies
     */
    private int friendCOM() {
        if(visibleAllies.size()==0)return 0;
        int ux = 0;
        int uy = 0;
        for(int id : visibleAllies){
            Robot ro = getRobot(id);
            ux += (ro.x-x);
            uy += (ro.y-y);
        }
        return directionTo(ux/visibleAllies.size(), uy/visibleAllies.size());
    }
    
    //Calculate weight of current square given visible map
    //if nexus blocked, move to diagonals
    //if not filled, move to nexus
    /*
     * Determines the weight of squares in cross positions and moves to
     * construct or unblock nexus construction depending on position of allies. 
     * Only moves depending on list of allies whose health is over a health
     * threshold to prioritize healing. 
     * @return  Action: move depending on ally position to construct or unblock nexus. 
     */
    private Action densityOverride(){
        int xweight = 0;
        int yweight = 0;
        HashSet<Integer> over30 = getHealthOverThreshold();
        if(over30.contains(map[3][1])&&over30.contains(map[3][2]))yweight++;
        if(over30.contains(map[3][4])&&over30.contains(map[3][5]))yweight--;
        if(over30.contains(map[1][3])&&over30.contains(map[2][3]))xweight++;
        if(over30.contains(map[4][3])&&over30.contains(map[5][3]))xweight--;
        if(xweight!=0 || yweight!=0)densityCounter++;
        if(densityCounter > 1 && closestDirTo(0)!=-1){
            int temp = densityCounter;
            densityCounter = 0;
            if(xweight > 1 && yweight > 1)return move(closestDirTo(7));
            if(xweight > 1 && yweight < 1)return move(closestDirTo(1));
            if(xweight < 1 && yweight < 1)return move(closestDirTo(3));
            if(xweight < 1 && yweight > 1)return move(closestDirTo(5));
            if(xweight < 1)return move(closestDirTo(4));
            if(xweight > 1)return move(closestDirTo(0));
            if(yweight < 1)return move(closestDirTo(2));
            if(yweight > 1)return move(closestDirTo(6));
            densityCounter = temp;
        }
        return null;
    }
    
    
    
    private ArrayList<int[]> findFriendlies(){
        ArrayList<int[]> ret = new ArrayList<int[]>(1);
        for(int i = 0; i<map.length; i++){
            for(int j = 0; j<map.length; j++){
                if(map[i][j]==id)continue;
                if(visibleAllies.contains(map[i][j])){
                    if((i==0 || j==0) || map[i-1][j-1]==bc.EMPTY){
                        int[] toAdd = {j+1,i-1};
                        ret.add(toAdd);
                    }
                    if((i==6 || j==6) || map[i+1][j+1]==bc.EMPTY){
                        int[] toAdd = {j+1,i+1};
                        ret.add(toAdd);
                    }
                    if((i==6 || j==0) || map[i+1][j-1]==bc.EMPTY){
                        int[] toAdd = {j-1,i+1};
                        ret.add(toAdd);
                    }
                    if((i==0 || j==6) || map[i-1][j+1]==bc.EMPTY){
                        int[] toAdd = {j+1,i-1};
                        ret.add(toAdd);
                    }
                }
            }
        }
        return ret;
    }
    
    private ArrayList<int[]> findBetterPartial(int level){
        ArrayList<int[]> ret = new ArrayList<int[]>(1);
        for(int i = 0; i<map.length; i++){
            for(int j = 0; j<map.length; j++){
                int total = 0;
                if(map[i][j]==bc.HOLE || (i>0 && map[i-1][j]==bc.HOLE) || (i<6 && map[i+1][j]==bc.HOLE) || (j>0 && map[i][j-1]==bc.HOLE) || (j<6 && map[i][j+1]==bc.HOLE))continue;
                if(i>0 && visibleAllies.contains(map[i-1][j]) && map[i-1][j]!=id){
                    total++;
                }
                if(i<6 && visibleAllies.contains(map[i+1][j]) && map[i+1][j]!=id){
                    total++;
                }
                if(j>0 && visibleAllies.contains(map[i][j-1]) && map[i][j-1]!=id){
                    total++;
                }
                if(j<6 && visibleAllies.contains(map[i][j+1]) && map[i][j+1]!=id){
                    total++;
                }
                if(total==level && total<4) {
                    if(j==0 || map[i][j-1]==bc.EMPTY){
                        int[] toAdd = {j-1,i};
                        ret.add(toAdd);
                    }
                    if(j==6 || map[i][j+1]==bc.EMPTY){
                        int[] toAdd = {j+1,i};
                        ret.add(toAdd);
                    }
                    if(i==0 || map[i-1][j]==bc.EMPTY){
                        int[] toAdd = {j,i-1};
                        ret.add(toAdd);
                    }
                    if(i==6 || map[i+1][j]==bc.EMPTY){
                        int[] toAdd = {j,i+1};
                        ret.add(toAdd);
                    }
                }
            }
        }
        return ret;
    }
    
    private ArrayList<int[]> findPartialsInVis(int level){
        ArrayList<int[]> ret = new ArrayList<int[]>(1);
        for(int i = 0; i<map.length; i++){
            for(int j = 0; j<map.length; j++){
                int total = 0;
                
                //if any in nexus are holes
                if(map[i][j]==bc.HOLE || (i>0 && map[i-1][j]==bc.HOLE) || (i<6 && map[i+1][j]==bc.HOLE) || (j>0 && map[i][j-1]==bc.HOLE) || (j<6 && map[i][j+1]==bc.HOLE))continue;
                
                //total = number of allies in nexus
                if(i>0 && visibleAllies.contains(map[i-1][j])){
                    total++;
                }
                if(i<6 && visibleAllies.contains(map[i+1][j])){
                    total++;
                }
                if(j>0 && visibleAllies.contains(map[i][j-1])){
                    total++;
                }
                if(j<6 && visibleAllies.contains(map[i][j+1])){
                    total++;
                }
                
                //if partial found, add absolute position 
                if(total==level && total<4) {
                    if(j==0 || map[i][j-1]==bc.EMPTY){
                        int[] toAdd = {j-1,i};
                        ret.add(toAdd);
                    }
                    if(j==6 || map[i][j+1]==bc.EMPTY){
                        int[] toAdd = {j+1,i};
                        ret.add(toAdd);
                    }
                    if(i==0 || map[i-1][j]==bc.EMPTY){
                        int[] toAdd = {j,i-1};
                        ret.add(toAdd);
                    }
                    if(i==6 || map[i+1][j]==bc.EMPTY){
                        int[] toAdd = {j,i+1};
                        ret.add(toAdd);
                    }
                }
            }
        }
        return ret;
    }
    
    /*
     * Check nexus position in north, south, east, west directions 
     * and returns direction 
     * @return  int: number of robots in nexus, out of 4
     */
    private int findConnLevel(){
        int[] partialLevels = {
            findPartialLevel(3,4),
            findPartialLevel(3,2),
            findPartialLevel(2,3),
            findPartialLevel(4,3)
        };
        int max = 0;
        for(int i = 0; i<4; i++){
            if(partialLevels[i]>max)max = partialLevels[i];
        }
        return max;
    }
    
    /*
     * Returns Finds how much of a nexus is fully built
     * @return  int: number of robots in nexus, out of 4
     */
    private int findPartialLevel(int x, int y){
        int total = 0;
        
        //check if possible nexus position
        if(map[y][x]==bc.HOLE || map[y][x+1]==bc.HOLE || map[y][x+1]==bc.HOLE || map[y-1][x]==bc.HOLE || map[y+1][x]==bc.HOLE)return 0;
        if(visibleAllies.contains(map[y-1][x])){
            total++;
        }
        if(visibleAllies.contains(map[y+1][x])){
            total++;
        }
        if(visibleAllies.contains(map[y][x-1])){
            total++;
        }
        if(visibleAllies.contains(map[y][x+1])){
            total++;
        }
        return total;
    }
    
    /*
     * Check if bot is a part of a partial nexus
     */
    private void checkPartial(){
        inPartial = findConnLevel()>0;
    }
    
    /*
     * Check if bot is a part of a fully constructed nexus
     */
    private void checkLattice(){
        boolean a = isLatticeSquare(3,4);
        boolean b = isLatticeSquare(3,2);
        boolean c = isLatticeSquare(4,3);
        boolean d = isLatticeSquare(2,3);
        
        inLattice = a || b || c || d;
    }
    
    /*
     * Checks if bot is disrupting the nexus.
     */
    private void checkDisruption(){
        if(disruptingNexus()){
            // log("disr");
            inLattice = false;
            inPartial = false;
        }
    }
    
    /*
     * Checks if input point is the center of a nexus.
     * @return  boolean: true if square is the center of a nexus
     *                   false otherwise
     */
    private boolean isLatticeSquare(int relx, int rely){
        // doesn't check for enemy yet
        boolean a = (visibleAllies.contains(map[relx+1][rely]));
        boolean b = (visibleAllies.contains(map[relx-1][rely]));
        boolean c = (visibleAllies.contains(map[relx][rely+1]));
        boolean d = (visibleAllies.contains(map[relx][rely-1]));
        boolean e = map[relx][rely]!=bc.HOLE;
        return a && b && c && d && e;
    }
    
    /*
     * Moves robot to (10, 10).
     * @return  Action: move in direction to 10, 10
     */
    private Action randDir(){
        //int dir = (int)(Math.random()*3);
        int dir = directionTo(10-x, 10-y);
        if (dir == -1) return null;
        dir = closestDirTo(dir);
        if (dir == -1) return null;
        return move(dir);
    }
    
     /*
     * Checks if initial input direction is possible to move in.
     * If not, returns next best possible move.
     * If no moves are possible, returns -1. 
     * @return  int: best direction to move in [-1, 7]
     */
    private int closestDirTo(int direction) {
        for (int i = 0; i < 4; i++) {
            int newDir = (direction + i) % 8;
            if (getInDirection(newDir) == bc.EMPTY && !disruptingNexus(newDir)) {
                // log(""+newDir);
                return newDir;
            }
            newDir = (direction + 8 - i) % 8;
            if (getInDirection(newDir) == bc.EMPTY && !disruptingNexus(newDir)) {
                // log(""+newDir);
                return newDir;
            }
        }
        return -1;
    }
    
    /*
     * Signal function based on id of bot to determine
     * if bot is an ally or not.
     */
    private int signalFunction() {
        return ((me.id+5) * (me.id+5) + me.team) % 16;
    }
    
    /*
     * Total number of visible allies not including self.
     */ 
    private int numberNearbyAllies() {
        return visibleAllies.size()-1; //not include itself
    }
    
    /*
     * Loops through possible directions to move in from [0,7] and 
     * returns first possible direction. 
     * @return  int: direction to move in. 
     */
    private int chooseDirection() {
        for (int i = 0; i < 7; i++) {
            int newDir = (dir + i) % 8;
            if (i != 4 && getInDirection(newDir) == bc.EMPTY) {
                dir = newDir;
                return newDir;
            }
        }
        dir = (dir + 4) % 8;
        if (getInDirection(dir) == bc.EMPTY) {
            return -1;
        }
        return dir;
    }

    /*
     * Checks if initial input direction is possible to move in.
     * If not, returns next best possible move.
     * If no moves are possible, returns -1. 
     * @return  int: best direction to move in [-1, 7]
     */
    private int chooseDirection(int direction) {
        for (int i = 0; i < 4; i++) {
            int newDir = (direction + i) % 8;
            if (getInDirection(newDir) == bc.EMPTY && !disruptingNexus(newDir)) {
                return newDir;
            }
            newDir = (direction + 8 - i) % 8;
            if (getInDirection(newDir) == bc.EMPTY && !disruptingNexus(newDir)) {
                return newDir;
            }
        }
        if (getInDirection((direction + 4) % 8) != bc.EMPTY || disruptingNexus((direction + 4) % 8)) {
            return -1;
        }
        return (direction + 4) % 8;
    }
    
    /*
     * Set random initial direction to travel based on function 
     */
    private void setInitialDirection() {
        dir = (2 * (id % 4) + 1);
    }
    
    /*
     * Returns opposite direction of first enemy bot if health drops
     * below threshold. 
     * Used to run away from enemy.
     * @return  int: direction away from first enemy
     */
    private int runAway() {
        ArrayList<Robot> visibleRobots = getVisibleRobots();
        for (Robot bot : visibleRobots) {
            if (visibleEnemies.contains(bot.id)) {
                int x = bot.x - me().x;
                int y = bot.y - me().y;
                
                //return's opposite direction of enemy bot
                return chooseDirection((directionTo(x,y)+4)%8);
            }
        }
        return -1;
    }
    
    /*
     * Wrapper function to handle combat functionality. 
     * Using a pacifist turtle stategy, will only actively attack if allies 
     * have a numbers advantage. Otherwise, will try and group or run away instead
     * @return  Action: attack enemy if number advantage and adjacent,
     *                  move to ally in combat if own bot not in combat,
     *                  null if enemies are more than 2 units away 
     */
    private Action checkAttack() {
        /*if (checkAdjacent() == 0) {
            return move(runAway());
        }*/
        
        if (checkAdjacent() > 0) {
            //find closest enemy
            //check if enemy is adjacent
                //if defend, hold ground
                //if attack, move forward
                
            int enemy_id = findClosestEnemy(me.x, me.y);
            Robot enemy = getRobot(enemy_id);
            
            if (adjacent(me().x, me().y, enemy.x, enemy.y)) {
                int attack_direction = directionTo((enemy.x-me().x), (enemy.y-me().y));
                inBattle = true;
                return attack(attack_direction);
            } else if (!inBattle && wrapAround() != -1) {
                // log("Wrap around called!");
                int temp_dir =chooseDirection(wrapAround());
                if (temp_dir == -1) return null;
                return move(temp_dir);
            } else if (Math.sqrt(Math.pow(enemy.x-me().x, 2) + Math.pow(enemy.y-me().y, 2)) >= 2) {
                return null;
            } 
            
            /*else if (attack) {
                int temp_dir = chooseDirection(directionTo(enemy.x-me.x, enemy.y-me.y));
                if (temp_dir == -1) return null;
                return move(temp_dir);
            }*/
        }
        //int temp_dir = chooseDirection(runAway());
        //if (temp_dir == -1) return null;
        //return move(temp_dir);
        return null;
    }
    
    /*
     * Compares number of allies to number of enemies in adjacent
     * squares, then returns 0 or 1 depending on whether allies or enemies 
     * have a numbers advantage. 
     * @return  int: 1 if number of allies > number of enemies
                     0 otherwise
     */
    //Checks number of allies and enemies in adjacent squares 
        //returns 1 if allies > enemies
        //else returns 0 
    private int checkAdjacent() {
        int num_enemies = 0;
        int num_allies = 0;
        if (enemyOrAlly(getRelativePos(-1, -1)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(-1, -1)) == 1) {
            num_allies++;
        }
        
        if (enemyOrAlly(getRelativePos(-1, 1)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(-1, 1)) == 1) {
            num_allies++;
        }
        
        if (enemyOrAlly(getRelativePos(1, -1)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(1, -1)) == 1) {
            num_allies++;
        }
        
        if (enemyOrAlly(getRelativePos(0, 1)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(0, 1)) == 1) {
            num_allies++;
        }
        
        if (enemyOrAlly(getRelativePos(1, 0)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(1, 0)) == 1) {
            num_allies++;
        }
        
        if (enemyOrAlly(getRelativePos(-1, 0)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(-1, 0)) == 1) {
            num_allies++;
        }
        
        if (enemyOrAlly(getRelativePos(0, -1)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(0, -1)) == 1) {
            num_allies++;
        }
        
        if (enemyOrAlly(getRelativePos(1, 1)) == 0) {
            num_enemies++;
        } else if (enemyOrAlly(getRelativePos(1, 1)) == 1) {
            num_allies++;
        }
        
        // log("num_a "+num_allies);
        // log("num_e "+num_enemies);
        
        if (num_allies > num_enemies) {
            return 1;
        } else {
            return 0;
        }
    }
    
    /*
     * Keeps a record and checks health of nearby ally robots.
     * Returns first ally robot where its health drops. 
     * @return  Robot: first ally robot where its health drops,
                       null otherwise
     */
    private Robot findAllyInCombat() {
        for (int id : visibleAllies) {
            Robot bot = getRobot(id);
            if (visibleAllyHealth.containsKey(id)) {
                int health = visibleAllyHealth.get(id);
                if (bot.health < health) {
                    return bot;
                }
            }
        }
        return null;
    }
    
    /*
     * Updates health records of visible nearby ally robots.
     */
    private void updateHealthRecords() {
        for (int id : visibleAllies) {
            Robot bot = getRobot(id);
            visibleAllyHealth.put(id, bot.health);
        }
    }
    
    /*
     * Returns direction of ally bot that is in battle. 
     * Used to assist allies that are in combat and to wrap around
     * to gang up on enemy units.
     * @return  int: direction to ally in combat
     */
    private int wrapAround() {
        Robot bot = findAllyInCombat();
        updateHealthRecords();
        if (bot == null) {
            return -1;
        } else {
            int direction = directionTo((bot.x-me.x), (bot.y-me.y));
            return direction;
        }
    }
    
    /*
     * Get relative bot location to own location.
     * @param   int: id of bot 
     * @return  int[]: relative x and y position of bot to own location
     */
    private int enemyOrAlly(int id) {
        if (visibleEnemies.contains(id)) {
            return 0;
        } else if (visibleAllies.contains(id)) {
            return 1;
        } else {
            return 2;
        }
    }

    /*
     * Find closest enemy and return that enemy's id.
     * @param   int: my x absolute value
     * @param   int: my y absolute value
     * @return  int: id of closest enemy unit
     */
    private int findClosestEnemy(int my_x, int my_y) {
        int enemy_id = -1;
        double distance = Integer.MAX_VALUE;
        for (int id : visibleEnemies) {
            Robot enemy = getRobot(id);
            int en_x = enemy.x;
            int en_y = enemy.y;
            double en_dist = Math.sqrt(Math.pow(en_x-my_x, 2) + Math.pow(en_y-my_y, 2));
            if (en_dist < distance) {
                enemy_id = enemy.id;
                distance = en_dist;
            }
        }
        return enemy_id;
    }

    /*
     * Checks enemy bot is adjacent to own location.
     * @param   int: my absolute x value
     * @param   int: my absolute y value
     * @param   int: enemy absolute x value
     * @param   int: enemy absolute y value
     * @return  boolean: true if enemy is adjacent, false otherwise 
     */
    private boolean adjacent(int my_x, int my_y, int en_x, int en_y) {
        int dx = Math.abs(my_x-en_x);
        int dy = Math.abs(my_y-en_y);
        if (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0)) {
            return true;
        } 
        return false;
    }
    
    /*
     * Moves units to target location set as (10, 10).
     * Used to aggregate units together - usually at the beginning of 
     * the round for faster expansion. 
     * @return  Action: move command to direction of (10,10) 
     */
    private Action aggregate() {
        int targx = 10;
        int targy = 10;
        if(x==targx && y==targy)return null;
        int temp_dir = chooseDirection(directionTo(targx - x, targy - y));
        if (temp_dir == -1) return null;
        return move(temp_dir);
    }
    
    /*
     * Get relative bot location to own location.
     * @param   int: id of bot 
     * @return  int[]: relative x and y position of bot to own location
     */
    private int[] findDirRelative(int id) {
        int[] ret = new int[2];
        int[][] map = getVisibleMap();
        for(int i = 0; i<map.length; i++){
            for(int j = 0; j<map.length; j++){
                if(map[i][j]==id){
                    ret[0] = j-3;
                    ret[1] = i-3;
                    return ret;
                }
            }
        }
        return ret;
    }
    
    /*
     * Returns relative direction given relative x and y inputs.
     * @param   int: relative x destination value
     * @param   int: relative y destination value
     * @return  int: return direction to relative destination 
     */
    private int directionTo(int x, int y) {
        if(x==0){
            if(y>0)return bc.SOUTH;
            if(y<0)return bc.NORTH;
            return -1;
        }
        double deg = Math.atan(-y/x);
        deg *= 180/3.14;
        if(x<0) deg += 180;
        deg = (deg + 360)%360;
        int dir = (int)((deg+23.5)/45);
        return dir;
    }
    
    /*
     * Returns squared relative distance to given point.
     * @param   int: x destination value
     * @param   int: y destination value
     * @return  double: distance to destination 
     */
    private double distanceTo(int x, int y){
        return Math.sqrt((double)(x*x)+(double)(y*y));
    }
    
    /*
     * Checks if signal matches signal function.
     * @return  boolean: true if signal is ally signal, false otherwise 
     */
    private boolean allySignal(Robot bot) {
        if (bot.id == me.id) {
            return true;
        }
        return (bot.signal == ((bot.id+5) * (bot.id+5) + me.team) % 16);
    }
    
    /*
     * Scans visible bots and sorts bot id's into ally or enemy HashSets 
     * given signal function. 
     */
    private void scan() {
        ArrayList<Robot> visibleRobots = getVisibleRobots();
        visibleAllies.clear();
        visibleEnemies.clear();
        for (Robot bot : visibleRobots) {
            int other_sig = bot.signal;
            int target_id = bot.id;
            boolean check = allySignal(bot);
            if (check) {
                visibleAllies.add(target_id);
            } else {
                visibleEnemies.add(target_id);
            }
        }
    }
    
    /*
     * Checks if given bot location is a valid nexus and corners are empty.
     * @return  boolean: true if is a valid nexus, false otherwise 
     */
    private boolean inMiddleOfNexus() {
        boolean setA;
        boolean setB;
        boolean setC;
        boolean setD;
        int dx = 0;
        int dy = 0;
        setA = (visibleAllies.contains(getRelativePos(dx, dy+1)) && visibleAllies.contains(getRelativePos(dx+1, dy)));
        setB = (visibleAllies.contains(getRelativePos(dx-1, dy)) && visibleAllies.contains(getRelativePos(dx, dy-1)));
        setC = (getRelativePos(dx+1, dy+1) < 2 || getRelativePos(dx+1, dy-1) < 2);
        setD = (getRelativePos(dx-1, dy+1) < 2 || getRelativePos(dx-1, dy-1) < 2);
        return (setA && setB && setC && setD);
    }
    
    /*
     * Check health to see if bot should stay in nexus to heal.
     * Method also takes into account number of nearby allies to 
     * prioritize expansion. 
     * @return  boolean: true if should stay to heal, false otherwise 
     */
    private boolean stayInMiddle() {
        return (me.health < healthThresh && numberNearbyAllies() >= 10 && inMiddleOfNexus());
    }
    
    /*
     * Checks if corners of current bot location are considered a nexus.
     * @return  boolean: true if are nexuses, false otherwise
     */
    private boolean disruptingNexus() {
        boolean a = isLatticeSquare(2, 4);
        boolean b = isLatticeSquare(4, 4);
        boolean c = isLatticeSquare(2, 2);
        boolean d = isLatticeSquare(4, 2);
        // log(""+a+" "+b+" "+c+" "+d);
        return a || b || c || d;
    }
    
    /*
     * Checks if bot going to given direction will disrupt the construction 
     * of a nexus. 
     * @param   int: direction of where bot is going
     * @return  boolean: true if WON'T disrupt nexus, false otherwise
     */
    private boolean disruptingNexus(int direction) {
        if (me().health > 60) return false;
        boolean setA;
        boolean setB;
        int dx;
        int dy;
        //check corners (direction)  and see if that is a center of a lattice 
        if (direction == 1) {
            dx = 1;
            dy = -1;
        } else if (direction == 3) {
            dx = -1;
            dy = -1;
        } else if (direction == 5) {
            dx = -1;
            dy = 1;
        } else {
            dx = 1;
            dy = 1;
        }
        setA = (visibleAllies.contains(getRelativePos(dx, dy+1)) && visibleAllies.contains(getRelativePos(dx+1, dy)));
        setB = (visibleAllies.contains(getRelativePos(dx-1, dy)) && visibleAllies.contains(getRelativePos(dx, dy-1)));
        return (setA && setB);
    }
    
    /*
     * Returns HashSet of all visible allies with health over 30.
     */
    private HashSet<Integer> getHealthOverThreshold() {
        HashSet<Integer> over30 = new HashSet<>();
        for (int id: visibleAllies) {
            if (getRobot(id).health >= healthThresh) {
                over30.add(id);
            }
        }
        // log(""+over30.size());
        return over30;
    }
    
    //Convert integer to string for log statements.
    private String s(int i){
        return Integer.toString(i);
    }

}
