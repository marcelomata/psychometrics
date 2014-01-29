/*
 * Copyright 2012 J. Patrick Meyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.itemanalysis.psychometrics.irt.model;

import com.itemanalysis.psychometrics.data.VariableName;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

/**
 * This version of the Generalized Partial Credit Model (GPCM) uses a discrimination
 * parameter, a difficulty parameter (b), and one or more threshold parameters. For an
 * item with M categories, there are M-1 threshold parameters (t). For k = 2,..., M,
 * Let Zk = sum_{v=2}^k {D*a*(theta-b+t_v)} if k>1 and Zk = 0 if k==1. The probability
 * of a response of k is given by, exp(Zk)/(1+sum_{k=2}^M {exp(Zk)}).
 *
 * This form of the GPCM is used in PARSCALE.
 *
 * The relationship with IrmGPCM is that the thresholds and difficulty in IrmGPCM2
 * can be combined to get teh step parameters in IrmGPCM such that, b_v = (b - t_v).
 * This decomposition of the step parameters is given in Muraki's 1992 article. It
 * differs from the decomposition of the step parameters in Linacre's parameterization
 * of the PCM as used in jMetrik and WINSTEPS. Linacre's parameterization is found
 * in IrmPCM.
 *
 */
public class IrmGPCM2 extends AbstractItemResponseModel{

    private double discrimination = 1.0;
    private double proposalDiscrimination = 1.0;
    private double discriminationStdError = 0.0;
    private double difficulty = 0.0;
    private double proposalDifficulty = 0.0;
    private double difficultyStdError = 0.0;
    private double D = 1.7;
    private double[] threshold;
    private double[] proposalThreshold;
    private double[] thresholdStdError;

    public IrmGPCM2(double discrimination, double difficulty, double[] threshold, double D){
        this.discrimination = discrimination;
        this.difficulty = difficulty;
        this.threshold = threshold;
        this.thresholdStdError = new double[threshold.length];
        this.D = D;
        ncatM1 = threshold.length;
        ncat = ncatM1+1;
        maxCategory = ncat;
        defaultScoreWeights();
    }

    /**
     * Computes probability of a response using parameters stored in the object.
     *
     * @param theta person proficiency value
     * @param category category for which the probability of a response is sought.
     * @return probability of responding in category
     */
    public double probability(double theta, int category){
        double t = numer(theta, category);
        double b = denom(theta);
        return t/b;
    }

    /**
     * Computes the expression for responding in a category.
     * It is the numerator for the probability of observing a response.
     *
     * @param  theta person proficiency value
     * @param category category for which probability is sought
     * @return expression for responding in category
     */
    private double numer(double theta, int category){
        double Zk = 0;
        double expZk = 0;
        double s = 0;

        //first category
        Zk = D*discrimination*(theta-difficulty);

        for(int k=0; k<category; k++){
            Zk += D*discrimination*(theta-difficulty+threshold[k]);
        }
        return Math.exp(Zk);
    }

    /**
     * Denominator is the sum of the numerators. This method is used for
     * computing the probability of a response.
     *
     * @param theta
     * @return
     */
    private double denom(double theta){
        double denom = 0.0;
        double expZk = 0.0;

        for(int k=0;k<ncat;k++){
            expZk = numer(theta, k);
            denom += expZk;
        }
        return denom;
    }

    /**
     * Partial derivative with respect to theta.
     *
     * @param theta person proficiency value
     * @return partial derivative at theta
     */
    public double derivTheta(double theta){
        double d1 = denom(theta);
        double d2 = d1*d1;
        double x1 = subCalcForDerivTheta(theta);
        double n1 = 0.0;
        double deriv = 0.0;
        double p1 = 0.0;
        double p2 = 0.0;

        for(int k=0;k<ncat;k++){
            n1 = numer(theta, k);
            p1 = (D*n1*(1.0+k)*discrimination)/d1;
            p2 = (n1*x1)/d2;
            deriv += scoreWeight[k]*(p1-p2);
        }

    	return deriv;

    }

    /**
     * Calculation needed for derivTheta().
     *
     * @param theta person proficiency value
     * @return
     */
    private double subCalcForDerivTheta(double theta){
        double sum = 0.0;
        for(int k=0;k<ncat;k++){
            sum += D*numer(theta, k)*(1.0+k)*discrimination;
        }
        return sum;
    }

    /**
     * computes the expected value using parameters stored in the object
     *
     * @param theta
     * @return
     */
    public double expectedValue(double theta){
        double ev = 0;
        for(int i=0;i<ncat;i++){
            ev += scoreWeight[i]*probability(theta, i);
        }
        return ev;
    }

    public double itemInformationAt(double theta){

        double T = 0;
        double prob = 0.0;
        double sum1 = 0.0;
        double sum2 = 0.0;
        double a2 = discrimination*discrimination;

        for(int i=0;i< ncat;i++){
            prob = probability(theta, i);
            T = scoreWeight[i];
            sum1 += T*T*prob;
            sum2 += T*prob;
        }

        double info = D*D*a2*(sum1 - Math.pow(sum2, 2));
        return info;

    }

    public void incrementMeanSigma(Mean mean, StandardDeviation sd){
        for(int i=0;i<ncatM1;i++){
            mean.increment(difficulty-threshold[i]);
            sd.increment(difficulty-threshold[i]);
        }

    }

    public void incrementMeanMean(Mean meanDiscrimination, Mean meanDifficulty){
        meanDiscrimination.increment(discrimination);
        for(int i=0;i<ncatM1;i++){
            meanDifficulty.increment(difficulty-threshold[i]);
        }

    }

    public void scale(double intercept, double slope){
        discrimination /= slope;
        discriminationStdError *= slope;
        difficulty = difficulty*slope + intercept;
        difficultyStdError *= slope;
        for(int i=0;i<ncatM1;i++){
            threshold[i] = threshold[i]*slope;
            thresholdStdError[i] = thresholdStdError[i]*slope;
        }
    }

    public int getNumberOfParameters(){
        return threshold.length+2;
    }

    /**
     * Returns the probability of a response with a linear transformatin of the parameters.
     * This transformation is such that Form X (New Form) is transformed to the scale of Form Y
     * (Old Form). It implements the backwards (New to Old) transformation as described in Kim
     * and Kolen.
     *
     * @param theta examinee proficiency parameter
     * @param category item response
     * @param intercept intercept coefficient of linear transformation
     * @param slope slope (i.e. scale) parameter of the linear transformation
     * @return probability of a response at values of linearly transformed item parameters
     */
    public double tStarProbability(double theta, int category, double intercept, double slope){
        if(category> maxCategory || category<minCategory) return 0;

        double Zk = 0;
        double expZk = 0;
        double numer = 0;
        double denom = 0;
        double a = discrimination/slope;
        double b = 0;
        double t = 0;

        for(int k=0;k<ncat;k++){
            Zk = 0;
            for(int v=1;v<(k+1);v++){
                b = difficulty*slope+intercept;
                t = threshold[v-1]*slope;
                Zk += D*a*(theta-(b-t));
            }
            expZk = Math.exp(Zk);
            if(k==category) numer = expZk;
            denom += expZk;
        }
        return numer/denom;
    }

    /**
     * computes the expected value using parameters stored in the object
     *
     * @param theta
     * @return
     */
    public double tStarExpectedValue(double theta, double intercept, double slope){
        double ev = 0;
        for(int i=1;i< ncat;i++){
            ev += scoreWeight[i]*tStarProbability(theta, i, intercept, slope);
        }
        return ev;
    }

    public double tSharpProbability(double theta, int category, double intercept, double slope){
        if(category>maxCategory || category<minCategory) return 0;

        double Zk = 0;
        double expZk = 0;
        double numer = 0;
        double denom = 0;
        double a = discrimination*slope;
        double b = 0;
        double t = 0;

        for(int k=0;k<ncat;k++){
            Zk = 0;
            for(int v=1;v<(k+1);v++){
                b = (difficulty-intercept)/slope;
                t = threshold[v-1]/slope;
                Zk += D*a*(theta-(b-t));
            }
            expZk = Math.exp(Zk);
            if(k==category) numer = expZk;
            denom += expZk;
        }
        return numer/denom;
    }

    public double tSharpExpectedValue(double theta, double intercept, double slope){
        double ev = 0;
        for(int i=1;i< ncat;i++){
            ev += scoreWeight[i]*tSharpProbability(theta, i, intercept, slope);
        }
        return ev;
    }

    public String toString(){
        String s = "[" + getDiscrimination() + ", " + getDifficulty();
        double[] sp = getStepParameters();
        for(int i=0;i<sp.length;i++){
            s+= ", " + sp[i];
        }
        s+= "]";
        return s;
    }

    public IrmType getType(){
        return IrmType.GPCM2;
    }

//=====================================================================================================================//
// GETTER AND SETTER METHODS MAINLY FOR USE WHEN ESTIMATING PARAMETERS                                                 //
//=====================================================================================================================//

    public double getDifficulty(){
        return difficulty;
    }

    public void setDifficulty(double difficulty){
        this.difficulty = difficulty;
    }

    public double getProposalDifficulty(){
        return proposalDifficulty;
    }

    public void setProposalDifficulty(double difficulty){
        this.proposalDifficulty = difficulty;
    }

    public double getDifficultyStdError(){
        return difficultyStdError;
    }

    public void setDifficultyStdError(double stdError){
        difficultyStdError = stdError;
    }

    public double getDiscrimination(){
        return discrimination;
    }

    public void setDiscrimination(double discrimination){
        this.discrimination = discrimination;
    }

    public void setProposalDiscrimination(double discrimination){
        this.proposalDiscrimination = discrimination;
    }

    public double getDiscriminationStdError(){
        return discriminationStdError;
    }

    public void setDiscriminationStdError(double stdError){
        discriminationStdError = stdError;
    }

    public double getGuessing(){
        return 0.0;
    }

    public void setGuessing(double guessing){
        throw new UnsupportedOperationException();
    }

    public void setProposalGuessing(double guessing){
        throw new UnsupportedOperationException();
    }

    public double getGuessingStdError(){
        throw new UnsupportedOperationException();
    }

    public void setGuessingStdError(double stdError){
        throw new UnsupportedOperationException();
    }

    public double[] getThresholdParameters(){
        return threshold;
    }

    public void setThresholdParameters(double[] thresholds){
        this.threshold = thresholds;
    }

    public void setProposalThresholds(double[] thresholds){
        this.proposalThreshold = threshold;
    }

    public double[] getThresholdStdError(){
        return thresholdStdError;
    }

    public void setThresholdStdError(double[] stdError){
        thresholdStdError = stdError;
    }

    public double[] getStepParameters(){
        double[] t = new double[ncatM1];
        for(int k=0;k<ncatM1;k++){
            t[k] = difficulty-threshold[k];
        }
        return t;
    }

    public void setStepParameters(double[] step){
        throw new UnsupportedOperationException();
    }

    public double[] getStepStdError(){
        throw new UnsupportedOperationException();
    }

    public void setStepStdError(double[] stdError){
        throw new UnsupportedOperationException();
    }

    public void acceptAllProposalValues(){
        if(!isFixed){
            this.difficulty = this.proposalDifficulty;
            this.discrimination = this.proposalDiscrimination;
            this.threshold = this.proposalThreshold;
        }

    }
//=====================================================================================================================//
// END GETTER AND SETTER METHODS                                                                                       //
//=====================================================================================================================//

}