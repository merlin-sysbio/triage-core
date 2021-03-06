/**
 * 
 */
package pt.uminho.ceb.biosystems.merlin.transporters.core.transport;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import pt.uminho.ceb.biosystems.merlin.bioapis.externalAPI.ncbi.EntrezLink.KINGDOM;
import pt.uminho.ceb.biosystems.merlin.transporters.core.compartments.GeneCompartments;
import pt.uminho.ceb.biosystems.merlin.transporters.core.transport.reactions.containerAssembly.TransportContainer;
import pt.uminho.ceb.biosystems.merlin.transporters.core.transport.reactions.containerAssembly.TransportReactionCI;
import pt.uminho.ceb.biosystems.merlin.utilities.External.ExternalRefSource;
import pt.uminho.ceb.biosystems.mew.biocomponents.container.components.StoichiometryValueCI;
import pt.uminho.ceb.biosystems.mew.biocomponents.container.io.exceptions.ReactionAlreadyExistsException;

/**
 * @author ODias
 *
 */
public class CompartmentaliseTransportContainer {

	private TransportContainer transportContainer;
	private Map<String,GeneCompartments> geneCompartmentsMap;
	private KINGDOM kingdom;
	private boolean isLoaded;

	/**
	 * @param transportContainer
	 * @param geneCompartmentsMap
	 * @param kingdom
	 */
	public CompartmentaliseTransportContainer(TransportContainer transportContainer,Map<String, GeneCompartments> geneCompartmentsMap, KINGDOM kingdom) {

		super();
		this.setTransportContainer(transportContainer);
		this.setGeneCompartmentsMap(geneCompartmentsMap);
		this.kingdom =  kingdom;
	}

	/**
	 * @throws IOException 
	 * @throws ReactionAlreadyExistsException 
	 * 
	 */
	public void loadCompartmentsToContainer() {
		
		this.transportContainer.addCompartments("extr".toUpperCase(), "extracellular",this.getOutside("extr").toUpperCase());
		
		Map<String, TransportReactionCI> reactions = new HashMap<>(this.transportContainer.getTransportReactions());
		
		Map<String, TransportReactionCI> ret = new HashMap<>();
		
		for(String reactionID : reactions.keySet()) {

			Map<String, Set<String>> compGene = new HashMap<String, Set<String>>();
			TransportReactionCI reactionCI = reactions.get(reactionID);  
			Set<String>  genes = reactionCI.getGenesIDs();

			for(String gene : genes) {
				
				if(this.containsGene(gene)) {

					GeneCompartments compartmentContainer = this.geneCompartmentsMap.get(gene);
					String abb = compartmentContainer.getPrimary_location_abb().toUpperCase();

					if(reactions.get(reactionID).getReversible() && abb.equalsIgnoreCase("extr")) {

						if(this.kingdom.equals(KINGDOM.Eukaryota))
							abb = "PLAS";
						else 
							abb = "outme";
					}

					this.transportContainer.addCompartments(abb.toUpperCase(), compartmentContainer.getPrimary_location(),this.getOutside(abb).toUpperCase());
					Set<String> abb_genes;

					if(compGene.containsKey(abb)) {

						abb_genes = compGene.get(abb);
						abb_genes.add(gene);
					}
					else {

						abb_genes = new HashSet<String>();
						abb_genes.add(gene);
					}
					
					if(!abb.equalsIgnoreCase("unkn")&&!abb.equalsIgnoreCase("cyt")&&!abb.equalsIgnoreCase("cyto")
							&&!abb.equalsIgnoreCase("cytop")&&!abb.equalsIgnoreCase("perip")&&!abb.equalsIgnoreCase("CYSK"))			
						compGene.put(abb, abb_genes);

					if(compartmentContainer.isDualLocalisation()) {

						for(String comparmtent:compartmentContainer.getSecondary_location_abb().keySet()) {

							abb = compartmentContainer.getSecondary_location_abb().get(comparmtent).toUpperCase();

							if(reactions.get(reactionID).getReversible() && abb.equalsIgnoreCase("extr")) {

								if(this.kingdom.equals(KINGDOM.Eukaryota))
									abb = "PLAS";
								else
									abb = "outme";
							}

							this.transportContainer.addCompartments(abb.toUpperCase(), comparmtent,this.getOutside(abb).toUpperCase());
							
							if(compGene.containsKey(abb)) {
								
								abb_genes = compGene.get(abb);
								abb_genes.add(gene);
							}
							else {
								
								abb_genes = new HashSet<String>();
								abb_genes.add(gene);
							}
							
							if(!abb.equalsIgnoreCase("unkn")&&!abb.equalsIgnoreCase("cyt")&&!abb.equalsIgnoreCase("cyto")&&
									!abb.equalsIgnoreCase("cytop")&&!abb.equalsIgnoreCase("perip")&&!abb.equalsIgnoreCase("CYSK"))								
								compGene.put(abb, abb_genes);
						}
					}
				}
				else {

					System.err.println("Gene not available in psort data:\t"+gene);
				}
			}
			
			boolean remove=false;
			int i = 0;
			
			for(String abb : compGene.keySet()) {
				
				remove=true;
				TransportReactionCI newReactionCI = reactionCI.clone();
				String newID = reactionID+"_C"+i;
				newReactionCI.setId(newID);
				newReactionCI.setName(newID);
				newReactionCI.setGenesIDs(compGene.get(abb));
				newReactionCI.setReactants(this.processMetabolite(newReactionCI.getReactants(), abb));
				newReactionCI.setProducts(this.processMetabolite(newReactionCI.getProducts(),  abb));
				ret.put(newID,newReactionCI);
				i++;
			}
			
			if(remove)				
				this.transportContainer.getReactions().remove(reactionID);
		}
		
		
		TransportContainer result = new TransportContainer(ret, this.transportContainer.getMetabolites(), this.transportContainer.getCompartments(),
				this.transportContainer.getGenes(), this.transportContainer.getKeggMiriam(), this.transportContainer.getChebiMiriam(), 
				this.transportContainer.getGenesProteins(), this.transportContainer.isKeggMetabolitesReactions(), this.transportContainer.isReactionsValidated(),
				this.transportContainer.getAlpha(), this.transportContainer.getMinimalFrequency(), this.transportContainer.getThreshold(), this.transportContainer.getBeta());

		this.transportContainer = result;
		try {
			
			transportContainer.verifyDepBetweenClass();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		this.setLoaded(true);
	}

	/**
	 * @param geneQuery
	 * @return
	 */
	private boolean containsGene(String geneQuery){
		
		for (String gene : this.geneCompartmentsMap.keySet())
			if(gene.equalsIgnoreCase(geneQuery))
				return true;

		return false;
	}

	/**
	 * @param path
	 * @throws IOException
	 */
	public void	createReactionsFiles(String path) throws IOException {

		Map<String,Set<String>> transportGenes = new HashMap<String, Set<String>>();
		File genes_transport = new File(path+"genes_transport_reactions.log");
		File reactions_log = new File(path+"transport_reactions_compartments.log");
		genes_transport.createNewFile();
		reactions_log.createNewFile();
		FileWriter fstream = new FileWriter(reactions_log), fstream_gene = new FileWriter(genes_transport);   
		BufferedWriter out = new BufferedWriter(fstream), out_gene = new BufferedWriter(fstream_gene);

		int reactionsCounter=0;

		for(String reaction:transportContainer.getReactions().keySet()) {

			if(transportContainer.getReactions().get(reaction).isAllMetabolitesHaveKEGGId()) {

				out.write("genes:\t" + this.transportContainer.getReactions().get(reaction).getGenesIDs()+"\n");

				for(String gene:this.transportContainer.getReactions().get(reaction).getGenesIDs()) {

					Set<String> tcs;
					if(transportGenes.containsKey(gene)) {

						tcs = transportGenes.get(gene);
					}
					else {

						tcs = new HashSet<String>();
					}
					tcs.addAll(this.transportContainer.getReactions().get(reaction).getProteinIds());
					transportGenes.put(gene, tcs);
				}
				out.write("transportReaction id:\t"+ reaction+"\t"+"\n");
				out.write("TC Number:\t" + transportContainer.getReactions().get(reaction).getProteinIds()+"\n");
				out.write("All TC numbers:\t" + this.transportContainer.getReactions().get(reaction).getProteinIds()+"\n");
				Map<String, StoichiometryValueCI> reactants = transportContainer.getReactions().get(reaction).getReactants();
				Map<String, StoichiometryValueCI> products = transportContainer.getReactions().get(reaction).getProducts();
				String concat  = "";

				for(String key :reactants.keySet()) {

					concat=concat.concat(reactants.get(key).getStoichiometryValue()+" "+transportContainer.getMetabolites().get(reactants.get(key).getMetaboliteId()).getName()+" ("+reactants.get(key).getCompartmentId())+") + ";
				}
				concat=concat.substring(0, concat.lastIndexOf("+")-1);

				if(transportContainer.getReactions().get(reaction).getReversible()) {

					concat=concat.concat(" <=> ");
				}
				else {

					concat=concat.concat(" => ");
				}

				for(String key :products.keySet()) {

					concat=concat.concat(products.get(key).getStoichiometryValue()+" "+transportContainer.getMetabolites().get(products.get(key).getMetaboliteId()).getName()+" ("+products.get(key).getCompartmentId())+") + ";
				}

				concat=concat.substring(0, concat.lastIndexOf("+")-1);
				out.write(concat+"\n");
				concat  = "";
				for(String key :reactants.keySet())
				{
					concat=concat.concat(reactants.get(key).getStoichiometryValue()+" "+
							ExternalRefSource.KEGG_CPD.getSourceId(this.getTransportContainer().getKeggMiriam().get(reactants.get(key).getMetaboliteId()))+
							" ("+reactants.get(key).getCompartmentId())+") + ";
				}
				concat=concat.substring(0, concat.lastIndexOf("+")-1);
				if(transportContainer.getReactions().get(reaction).getReversible())
				{
					concat=concat.concat(" <=> ");
				}
				else
				{
					concat=concat.concat(" => ");
				}

				for(String key :products.keySet())
				{
					concat=concat.concat(products.get(key).getStoichiometryValue()+" "+
							ExternalRefSource.KEGG_CPD.getSourceId(this.getTransportContainer().getKeggMiriam().get(products.get(key).getMetaboliteId()))+
							" ("+products.get(key).getCompartmentId())+") + ";

				}
				concat=concat.substring(0, concat.lastIndexOf("+")-1);
				out.write(concat+"\n\n");
				//					if(this.transportContainer.getReactions().get(reaction).getGenesIDs().contains("KLLA0B00264g"))
				//					{
				//						//System.out.println(reactants);
				//						//System.out.println(reaction);
				//						System.out.println(concat);
				//					}
				reactionsCounter++;
			}
		}
		out.write("reactions counter:\t"+reactionsCounter+"\n");
		out.write("transport container reactions size:\t"+transportContainer.getReactions().size()+"\n");
		out.close();

		for(String gene:transportGenes.keySet())
		{
			if(this.geneCompartmentsMap.get(gene).isDualLocalisation())
			{
				out_gene.write("gene:\t"+gene+"\t"+transportGenes.get(gene)+"\t"+this.geneCompartmentsMap.get(gene).getPrimary_location_abb()+"\t"+this.geneCompartmentsMap.get(gene).getSecondary_location_abb()+"\n");
			}
			else
			{
				out_gene.write("gene:\t"+gene+"\t"+transportGenes.get(gene)+"\t"+this.geneCompartmentsMap.get(gene).getPrimary_location_abb()+"\n");
			}
		}

		out_gene.close();
	}

	/**
	 * @param path
	 * @throws IOException
	 */
	public void createReactionsAnnotationsTabFiles(String path) throws IOException {
		
		File genes_transport = new File(path+"genes_transport_reactionsTab.log");
		genes_transport.createNewFile();
		FileWriter fstream_gene = new FileWriter(genes_transport);   
		BufferedWriter out = new BufferedWriter(fstream_gene);

		out.write("transportReaction\tgenes\treaction");

		int reactionsCounter=0;
		for(String reaction:transportContainer.getReactions().keySet()) {
			
			if(transportContainer.getReactions().get(reaction).isAllMetabolitesHaveKEGGId()) {
				
				out.write(reaction+"\t"+this.transportContainer.getReactions().get(reaction).getGenesIDs().toString().replaceAll("\\[","").replaceAll("\\]","")+"\t");
				Map<String, StoichiometryValueCI> reactants = transportContainer.getReactions().get(reaction).getReactants();
				Map<String, StoichiometryValueCI> products = transportContainer.getReactions().get(reaction).getProducts();
				String concat  = "";
				
				for(String key :reactants.keySet()) {
					
					concat=concat.concat(reactants.get(key).getStoichiometryValue()+" "+
							ExternalRefSource.KEGG_CPD.getSourceId(this.getTransportContainer().getKeggMiriam().get(reactants.get(key).getMetaboliteId()))+
							" "+reactants.get(key).getCompartmentId())+" + ";
				}
				concat=concat.substring(0, concat.lastIndexOf("+")-1);
				
				if(transportContainer.getReactions().get(reaction).getReversible())
					concat=concat.concat(" <=> ");
				else
					concat=concat.concat(" => ");

				for(String key :products.keySet()) {
					
					concat=concat.concat(products.get(key).getStoichiometryValue()+" "+
							ExternalRefSource.KEGG_CPD.getSourceId(this.getTransportContainer().getKeggMiriam().get(products.get(key).getMetaboliteId()))+
							" "+products.get(key).getCompartmentId())+" + ";
				}
				concat=concat.substring(0, concat.lastIndexOf("+")-1);
				out.write(concat+"\n");
				reactionsCounter++;
			}
		}
		out.write("reactions counter:\t"+reactionsCounter+"\n");
		out.write("transport container reactions size:\t"+transportContainer.getReactions().size()+"\n");
		out.close();
	}

	/**
	 * @param metaboliteMap
	 * @param compartmentID
	 * @param reactant
	 * @return 
	 */
	private Map<String, StoichiometryValueCI> processMetabolite(Map<String, StoichiometryValueCI> metaboliteMap, String compartmentID){
		
		String interior;
		if(this.kingdom.equals(KINGDOM.Eukaryota))			
			interior = "cyto";
		else
			interior = "cytop";

		Map<String, StoichiometryValueCI> newMetaboliteMap = new HashMap<String, StoichiometryValueCI>();
		
		for(String id : metaboliteMap.keySet()) {
			
			StoichiometryValueCI metabolite = metaboliteMap.get(id);
			String localisation = metabolite.getCompartmentId();
			
			if(localisation.equals("out")) {
				String compartment = "";
				
				if(compartmentID.equalsIgnoreCase("plas") || compartmentID.equalsIgnoreCase("outme"))
					compartment = "extr";
				else if(compartmentID.equalsIgnoreCase("cytmem"))
					compartment = "perip";
				else if (compartmentID.equalsIgnoreCase(interior))
					compartment = compartmentID;
				else
					compartment = interior;
				
				metabolite.setCompartmentId(compartment.toUpperCase());
				
				if(!transportContainer.getCompartments().containsKey(compartment))
					this.transportContainer.addCompartments(compartment.toUpperCase(), compartment,this.getOutside(compartment).toUpperCase());
			}
			else {
				
				String compartment = "";
				
				if(compartmentID.equalsIgnoreCase("plas") || compartmentID.equalsIgnoreCase("cytmem"))
					compartment = interior;
				else if (compartmentID.equalsIgnoreCase("outme"))
					compartment = "perip";
				else if (compartmentID.equalsIgnoreCase(interior))
					compartment = interior;
				else
					compartment = compartmentID;
				
				metabolite.setCompartmentId(compartment.toUpperCase());
				
				if(!transportContainer.getCompartments().containsKey(compartment))
					this.transportContainer.addCompartments(compartment.toUpperCase(), compartment,this.getOutside(compartment).toUpperCase());
			}
			newMetaboliteMap.put(id, metabolite);
		}

		return newMetaboliteMap;
	}

	/**
	 * @param compartmentID
	 * @return
	 */
	private String getOutside(String compartmentID) {

		if(compartmentID.equalsIgnoreCase("extr")) {

			return "";
		}
		if(compartmentID.equalsIgnoreCase("unkn"))
		{
			return "unknown";
		}
		if(compartmentID.equalsIgnoreCase("cytmem"))
		{
			return "perip";
		}
		if(compartmentID.equalsIgnoreCase("perip"))
		{
			return "outme";
		}
		if(compartmentID.equalsIgnoreCase("outme"))
		{
			return "extr";
		}
		if(compartmentID.equalsIgnoreCase("cytop"))
		{
			return "cytmem";
		}
		if(compartmentID.equalsIgnoreCase("pla") || compartmentID.equalsIgnoreCase("plas"))
		{
			return "extr";
		}
		else if(compartmentID.equalsIgnoreCase("gol") || compartmentID.equalsIgnoreCase("golg"))
		{
			return "golmem";
		}
		else if(compartmentID.equalsIgnoreCase("vac") || compartmentID.equalsIgnoreCase("vacu"))
		{
			return "vacmem";
		}
		else if(compartmentID.equalsIgnoreCase("mit") || compartmentID.equalsIgnoreCase("mito"))
		{
			return "mitmem";
		}
		else if(compartmentID.equalsIgnoreCase("end") || compartmentID.equalsIgnoreCase("E.R.")) 
		{
			return "ermem";
		}
		else if(compartmentID.equalsIgnoreCase("nuc") || compartmentID.equalsIgnoreCase("nucl"))
		{
			return "nucmem";
		}
		else if(compartmentID.equalsIgnoreCase("cyt") || compartmentID.equalsIgnoreCase("cyto"))
		{
			return "plas";
		}
		else if(compartmentID.equalsIgnoreCase("csk") || compartmentID.equalsIgnoreCase("cysk"))
		{
			return "plas";
		}
		else if(compartmentID.equalsIgnoreCase("pox") || compartmentID.equalsIgnoreCase("pero"))
		{
			return "permem";
		}
		else
		{
			return "cyto";
		}
	}


	/**
	 * @return the transportContainer
	 */
	public TransportContainer getTransportContainer() {
		return transportContainer;
	}


	/**
	 * @param transportContainer the transportContainer to set
	 */
	public void setTransportContainer(TransportContainer transportContainer) {
		this.transportContainer = transportContainer;
	}


	/**
	 * @return the geneCompartmentsMap
	 */
	public Map<String,GeneCompartments> getGeneCompartmentsMap() {
		return geneCompartmentsMap;
	}


	/**
	 * @param geneCompartmentsMap the geneCompartmentsMap to set
	 */
	public void setGeneCompartmentsMap(Map<String,GeneCompartments> geneCompartmentsMap) {
		this.geneCompartmentsMap = geneCompartmentsMap;
	}

	/**
	 * @return the isLoaded
	 */
	public boolean isLoaded() {
		return isLoaded;
	}

	/**
	 * @param isLoaded the isLoaded to set
	 */
	public void setLoaded(boolean isLoaded) {
		this.isLoaded = isLoaded;
	}

}
