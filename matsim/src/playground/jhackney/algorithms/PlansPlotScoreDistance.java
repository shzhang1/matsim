package playground.jhackney.algorithms;

import org.matsim.basic.v01.BasicPlanImpl.LegIterator;
import org.matsim.plans.Leg;
import org.matsim.plans.Person;
import org.matsim.plans.Plan;
import org.matsim.plans.Plans;
import org.matsim.plans.algorithms.PersonAlgorithm;
import org.matsim.plans.algorithms.PersonAlgorithmI;
import org.matsim.utils.charts.XYScatterChart;

public class PlansPlotScoreDistance extends PersonAlgorithm implements PersonAlgorithmI{
	double[] dist=null;
	double[] score=null;
	int i=0;

	public PlansPlotScoreDistance(Plans plans) {
		super();
		this.dist= new double[plans.getPersons().size()];
		this.score= new double[plans.getPersons().size()];
	}

	@Override
	public void run(Person person) {
		// TODO Auto-generated method stub
		Plan p = person.getSelectedPlan();
		dist[i]=0;
		score[i]= p.getScore();
		LegIterator ai=p.getIteratorLeg();
		while(ai.hasNext()){
			Leg l = (Leg) ai.next();
			dist[i]+=l.getRoute().getDist();
		}
		i++;
	}
	public double[] getDist(){
		return this.dist;
	}
	public double[] getScore(){
		return this.score;
	}
	public void plot(String outdir, String name){
		String filename=outdir+"DistanceScore"+name+".png";
		String plotname="Distance vs. Score"+name;
		createXYScatterChart(filename, plotname, "Distance m", "Score", dist, score);
	}
	
	private void createXYScatterChart(final String filename, final String title, final String xname, final String yname, double[] x, double[] y) {
		XYScatterChart chart = new XYScatterChart(title, xname, yname);
		chart.addSeries(xname, x, y);
//		chart.addSeries("serie 2", new double[] {1.0, 5.0, 2.0, 4.0, 3.0}, new double[] {2.0, 3.0, 3.0, 1.5, 4.5});
		chart.saveAsPng(filename, 800, 600);
	}
}
