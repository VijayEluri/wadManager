package services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import models.BankAccount;
import models.Operation;
import models.OperationPrevision;
import models.Tag;
import models.TagStatValueObject;
import models.User;
import controllers.Application;

public class BankAccountServiceImpl implements BankAccountService {

	public void delete(Long id) {
		User u = Application.getAuthUser();
		//@TODO verify he have rights to do what
		
		BankAccount bankAccount = BankAccount.findById(id);
		bankAccount.delete();
	}

	public void save(BankAccount ba) {
		ba.save();
	}

	public BankAccount getById(Long id) {
		return BankAccount.findById(id);
	}

	public void deleteOperation(Long id) {
		/*
		Operation.findById(id).delete();
		*/
		Operation op = Operation.findById(id);
		op.delete();
	}

	public Operation getOperationById(Long id) {
		return Operation.findById(id);
	}

	public void saveOperation(Operation op) {
		op.save();
	}

	public List<BankAccount> getByUser(User u) {
		return BankAccount.find("byUser", u).fetch();
	}

	@Override
	public boolean operationExists(Operation op) {
		long size = Operation.count("amount = ? and date = ?", op.amount, op.date);
		
		if (size ==0) {
			return false;
		}
		
		return true;
	}

	public BankAccount getByOperation(Operation op) {
		//BankAccount ba =  BankAccount.find("byOperation", op).first();
		BankAccount ba = BankAccount.findById(op.bankAccount.getId());
		return ba;
	}

	@Override
	public BigDecimal getAmountAt(BankAccount ba, Date date) {
		List<Operation> operations = Operation.find("bankAccount = ? and date < ? order by date ASC", ba, date).fetch();
		
		BigDecimal result = BigDecimal.valueOf(0d);
		for (Operation op: operations) {
			if (!op.fictive) 
				result = result.add(BigDecimal.valueOf(op.amount));
		}
		return result;
	}
	
	public BigDecimal calculateEstimation(BankAccount ba, Tag ta) {
		TagStatValueObject stat = getStatForTag(ta, ba);
		
		return stat.getMajoredEstimation();
	}
	
	public BigDecimal calculateBudgetForTag(BankAccount ba, Date dateBegin, Date dateEnd, Tag ta) {
		List<Operation> operations = 
					/*Operation.find("select distinct op from Operation op " +
							"           join op.tags as t" +
							"			where          "+
							"			bankAccount = :bankAccount and (date < :dateBegin and date > :dateEnd) " +
							"			order by date ASC")
										//.bind("tag", ta)
										.bind("bankAccount", ba)
										.bind("dateBegin", dateBegin)
										.bind("dateEnd", dateEnd).fetch();*/
					Operation.find("select op from Operation op " +
							"		join op.tags as t" +
							"		where "+
							"       t in (:tag) and" +
							"       op.bankAccount = (:bankAccount) and "+
							"		(op.date >= :dateBegin and op.date < :dateEnd)")
							.bind("dateBegin", dateBegin)
							.bind("dateEnd", dateEnd)
							.bind("bankAccount", ba)
							.bind("tag", ta)
							.fetch();
		
		BigDecimal result = BigDecimal.valueOf(0d);
		for (Operation op: operations) {
			result = result.add(BigDecimal.valueOf(op.amount));
		}
		return result;
	}
	
	public Map<Tag, TagStatValueObject>getAllStatForBankAccount(BankAccount ba) {
		List<Tag> tags = Tag.find("byUser",Application.getAuthUser()).fetch();
		
		Map<Tag, TagStatValueObject> res = new TreeMap<Tag, TagStatValueObject>();
		
		for (Tag tag : tags) {
			//System.out.println("stat for "+tag);
			res.put(tag, getStatForTag(tag, ba));
		}
		return res;
	}
	
	public TagStatValueObject getStatForTag(Tag t, BankAccount ba) {
		TagStatValueObject res = new TagStatValueObject();
		
		Calendar cal = Calendar.getInstance();
		
		Date today = cal.getTime();
		
		//int year = cal.get(Calendar.YEAR);
		cal.set(2010, 1, 1);
		
		while (cal.getTime().compareTo(today)<0)  {
			Date dateBegin = cal.getTime();
			cal.add(Calendar.MONTH, 1);
			Date dateEnd = cal.getTime();
			BigDecimal budget = calculateBudgetForTag(ba, dateBegin, dateEnd, t);
			//System.out.println("add "+dateBegin+" "+budget);
			res.addValue(dateBegin, budget);
		}
		
		return res;
		
	}

	public Collection<OperationPrevision> getAllOperationPrevisions(Date begin,
			Date end) {
		Collection <OperationPrevision> result = OperationPrevision.find("date >= ? and date < ?",begin, end).fetch();
		if (result == null) {
			result = new ArrayList<OperationPrevision>();
		}
		return result;
	}

	@Override
	public BankAccount getByOperationPrevision(OperationPrevision op) {
		BankAccount ba = BankAccount.findById(op.bankAccount.getId());
		return ba;
	}

	@Override
	public OperationPrevision getOperationPrevisionById(Long id) {
		return OperationPrevision.findById(id);
	}

	@Override
	public void saveOperationPrevision(OperationPrevision op) {
		op.save();
	}
	
	//need to remove after fix
	@Override
	public int fixBadTag() {
		List<Tag> tags = Tag.findAll();
		
		int fixed = 0;
		
		for (Tag tag : tags) {
			if (tag.user == null) {
				fixThisTag(tag);
				//tag.delete();
				fixed++;
			}
		}
		
		
		return fixed;
	}

	private void fixThisTag(Tag tag) {
		TagService ts = new TagService();
		Tag realTag = ts.getOrCreateByName(tag.name);
		
		List<Operation> operations = Operation.find("select op from Operation op " +
				"		join op.tags as t" +
				"		where "+
				"       t in (:tag)")
				.bind("tag", tag).fetch();
		
		
		
		for (Operation op : operations) {
			op.tags.remove(tag);
			op.tags.add(realTag);
			op.save();
		}
		
	}


	
	
}
